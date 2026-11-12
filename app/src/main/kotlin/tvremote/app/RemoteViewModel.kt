package tvremote.app

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tvremote.core.ClientIdentity
import tvremote.core.InvalidCodeException
import tvremote.core.KeyCodes
import tvremote.core.NotPairedException
import tvremote.core.PairingClient
import tvremote.core.RemoteClient
import tvremote.core.RemoteListener
import tvremote.core.TvInfo
import tvremote.core.VolumeInfo
import java.io.File
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class Screen { Setup, Pairing, Remote }

enum class Connection { Disconnected, Connecting, Connected }

data class SavedTv(val name: String, val host: String)

data class UiState(
    val screen: Screen = Screen.Setup,
    val tv: SavedTv? = null,
    val found: List<FoundTv> = emptyList(),
    val searching: Boolean = false,
    val connection: Connection = Connection.Disconnected,
    val pairingBusy: Boolean = false,
    val pairingReady: Boolean = false,
    val tvInfo: TvInfo? = null,
    val volume: VolumeInfo? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteViewModel(app: Application) : AndroidViewModel(app), RemoteActions {

    private val prefs = app.getSharedPreferences("tv", Context.MODE_PRIVATE)
    private val identityFile = File(app.filesDir, "identity.p12")
    /** Keys must reach the TV in order: one sender at a time. */
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var identity: ClientIdentity? = null
    private var remote: RemoteClient? = null
    private var pairing: PairingClient? = null
    private var discovery: TvDiscovery? = null
    private var connectJob: Job? = null
    private var foreground = false

    init {
        val host = prefs.getString("host", null)
        if (host != null) {
            _state.update { it.copy(screen = Screen.Remote, tv = SavedTv(prefs.getString("name", null) ?: host, host)) }
        }
    }

    // ---- lifecycle -------------------------------------------------------

    fun onForeground() {
        foreground = true
        when (_state.value.screen) {
            Screen.Remote -> connect()
            Screen.Setup -> startSearch()
            Screen.Pairing -> {}
        }
    }

    fun onBackground() {
        foreground = false
        stopSearch()
        disconnect()
    }

    // ---- discovery / setup ----------------------------------------------

    override fun startSearch() {
        if (discovery == null) {
            discovery = TvDiscovery(getApplication()) { tv ->
                _state.update { s -> if (s.found.any { it.host == tv.host }) s else s.copy(found = s.found + tv) }
            }
        }
        _state.update { it.copy(found = emptyList(), searching = true) }
        discovery?.start()
        viewModelScope.launch {
            delay(15_000)
            _state.update { it.copy(searching = false) }
        }
    }

    private fun stopSearch() {
        discovery?.stop()
        _state.update { it.copy(searching = false) }
    }

    override fun chooseTv(name: String, host: String) {
        val clean = host.trim()
        if (clean.isEmpty()) return
        stopSearch()
        prefs.edit().putString("host", clean).putString("name", name.ifBlank { clean }).apply()
        _state.update { it.copy(tv = SavedTv(name.ifBlank { clean }, clean), screen = Screen.Remote, message = null) }
        connect()
    }

    override fun changeTv() {
        disconnect()
        cancelPairing()
        prefs.edit().clear().apply()
        _state.update { UiState(screen = Screen.Setup) }
        startSearch()
    }

    // ---- connection -------------------------------------------------------

    override fun connect() {
        val tv = _state.value.tv ?: return
        if (remote?.isConnected == true || connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            _state.update { it.copy(connection = Connection.Connecting, message = null) }
            try {
                val client = withContext(io) {
                    RemoteClient(tv.host, identity(), listener).also { it.connect() }
                }
                remote = client
            } catch (e: NotPairedException) {
                _state.update { it.copy(connection = Connection.Disconnected) }
                startPairing()
            } catch (e: Exception) {
                _state.update { it.copy(connection = Connection.Disconnected, message = friendly(e)) }
            }
        }
    }

    private fun disconnect() {
        connectJob?.cancel()
        remote?.let { r -> viewModelScope.launch(io) { r.close() } }
        remote = null
        _state.update { it.copy(connection = Connection.Disconnected) }
    }

    private val listener = object : RemoteListener {
        override fun onReady(info: TvInfo) =
            _state.update { it.copy(connection = Connection.Connected, tvInfo = info, message = null) }

        override fun onVolumeChanged(volume: VolumeInfo) = _state.update { it.copy(volume = volume) }

        override fun onDisconnected(error: Throwable?) {
            _state.update { it.copy(connection = Connection.Disconnected) }
            remote = null
            // The TV drops idle connections and goes away in standby: retry quietly.
            if (error != null && foreground) viewModelScope.launch { delay(1500); if (foreground) connect() }
        }
    }

    // ---- pairing -----------------------------------------------------------

    private fun startPairing(message: String? = null) {
        val tv = _state.value.tv ?: return
        cancelPairing()
        _state.update { it.copy(screen = Screen.Pairing, pairingBusy = true, pairingReady = false, message = message) }
        viewModelScope.launch {
            try {
                val p = withContext(io) {
                    PairingClient(tv.host, identity(), "${Build.MANUFACTURER} ${Build.MODEL}").also { it.start() }
                }
                pairing = p
                _state.update { it.copy(pairingBusy = false, pairingReady = true) }
            } catch (e: Exception) {
                _state.update { it.copy(pairingBusy = false, message = friendly(e)) }
            }
        }
    }

    override fun retryPairing() = startPairing()

    override fun submitCode(code: String) {
        val p = pairing ?: return startPairing()
        _state.update { it.copy(pairingBusy = true, message = null) }
        viewModelScope.launch {
            try {
                withContext(io) { p.finish(code) }
                cancelPairing()
                _state.update { it.copy(screen = Screen.Remote, pairingBusy = false, pairingReady = false) }
                connect()
            } catch (e: InvalidCodeException) {
                if (e.cause != null) {
                    // The TV refused it and closed the session: ask for a new code.
                    startPairing("${e.message}. Un nouveau code va s'afficher sur la télé.")
                } else {
                    // Wrong checksum: the TV still shows the same code, let the user retype it.
                    _state.update { it.copy(pairingBusy = false, message = e.message) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(pairingBusy = false, pairingReady = false, message = friendly(e)) }
            }
        }
    }

    fun cancelPairing() {
        pairing?.let { p -> viewModelScope.launch(io) { p.close() } }
        pairing = null
    }

    override fun backToSetup() {
        cancelPairing()
        changeTv()
    }

    // ---- commands ---------------------------------------------------------

    override fun key(code: Int) = send { it.sendKey(code) }

    override fun openApp(link: String) = send { it.openLink(link) }

    override fun typeText(text: String) = send { r ->
        text.forEach { c -> KeyCodes.forChar(c)?.let { r.sendKey(it) } }
    }

    private fun send(action: (RemoteClient) -> Unit) {
        val r = remote
        if (r == null || !r.isConnected) {
            connect()
            return
        }
        viewModelScope.launch(io) {
            runCatching { action(r) }.onFailure { connect() }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---- helpers ------------------------------------------------------------

    private fun identity(): ClientIdentity {
        identity?.let { return it }
        val id = if (identityFile.exists()) {
            runCatching { ClientIdentity.fromPkcs12(identityFile.readBytes()) }.getOrNull()
        } else null
        return (id ?: ClientIdentity.generate().also { identityFile.writeBytes(it.toPkcs12()) })
            .also { identity = it }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is SocketTimeoutException, is NoRouteToHostException ->
            "La télé ne répond pas. Vérifie qu'elle est allumée et que le téléphone est sur le même Wi-Fi."
        is ConnectException ->
            "Connexion refusée. La télé est peut-être en veille profonde, ou l'adresse IP n'est pas la bonne."
        is UnknownHostException -> "Adresse IP invalide."
        else -> e.message ?: e.javaClass.simpleName
    }

    override fun onCleared() {
        stopSearch()
        cancelPairing()
        remote?.close()
    }
}
