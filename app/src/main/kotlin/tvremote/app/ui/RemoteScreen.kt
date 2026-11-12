package tvremote.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tvremote.app.Connection
import tvremote.app.RemoteActions
import tvremote.app.UiState
import tvremote.core.KeyCodes

/** Shortcuts opened through Android TV app links (ignored by the TV if the app is missing). */
private val APPS = listOf(
    "YouTube" to "https://www.youtube.com",
    "Netflix" to "https://www.netflix.com/title",
    "Prime Video" to "https://app.primevideo.com",
    "Disney+" to "https://www.disneyplus.com",
)

private enum class Panel { Apps, Digits, Keyboard }

@Composable
fun RemoteScreen(state: UiState, vm: RemoteActions) {
    var panel by rememberSaveable { mutableStateOf(Panel.Apps) }
    val k: (Int) -> Unit = vm::key

    Surface(Modifier.fillMaxSize(), color = Palette.Background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(state, vm)
            StatusBanner(state, vm)

            // Power, input, settings
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundKey(Icons.Rounded.PowerSettingsNew, "Marche / arrêt", color = Palette.Power) { k(KeyCodes.POWER) }
                RoundKey(Icons.AutoMirrored.Rounded.Input, "Source") { k(KeyCodes.TV_INPUT) }
                RoundKey(Icons.Rounded.Search, "Recherche") { k(KeyCodes.SEARCH) }
                RoundKey(Icons.Rounded.Settings, "Paramètres") { k(KeyCodes.SETTINGS) }
            }

            DPad(k)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundKey(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") { k(KeyCodes.BACK) }
                RoundKey(Icons.Rounded.Home, "Accueil") { k(KeyCodes.HOME) }
                RoundKey(Icons.Rounded.Menu, "Menu") { k(KeyCodes.MENU) }
                RoundKey(Icons.Rounded.Info, "Infos") { k(KeyCodes.INFO) }
            }

            // Volume and channels, like the rockers on a real remote
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Rocker("VOL", Modifier.weight(1f), up = { k(KeyCodes.VOLUME_UP) }, down = { k(KeyCodes.VOLUME_DOWN) })
                Column(
                    Modifier.weight(0.8f).height(150.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RoundKey(Icons.AutoMirrored.Rounded.VolumeOff, "Couper le son") { k(KeyCodes.VOLUME_MUTE) }
                    Spacer(Modifier.height(10.dp))
                    VolumeLevel(state)
                }
                Rocker("CH", Modifier.weight(1f), up = { k(KeyCodes.CHANNEL_UP) }, down = { k(KeyCodes.CHANNEL_DOWN) })
            }

            // Media
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundKey(Icons.Rounded.SkipPrevious, "Précédent", size = 52.dp) { k(KeyCodes.MEDIA_PREVIOUS) }
                RoundKey(Icons.Rounded.FastRewind, "Retour rapide", size = 52.dp, repeat = true) { k(KeyCodes.MEDIA_REWIND) }
                RoundKey(Icons.Rounded.PlayArrow, "Lecture / pause", size = 52.dp) { k(KeyCodes.MEDIA_PLAY_PAUSE) }
                RoundKey(Icons.Rounded.FastForward, "Avance rapide", size = 52.dp, repeat = true) { k(KeyCodes.MEDIA_FAST_FORWARD) }
                RoundKey(Icons.Rounded.SkipNext, "Suivant", size = 52.dp) { k(KeyCodes.MEDIA_NEXT) }
            }

            // Color keys
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Palette.Red to KeyCodes.PROG_RED, Palette.Green to KeyCodes.PROG_GREEN,
                    Palette.Yellow to KeyCodes.PROG_YELLOW, Palette.Blue to KeyCodes.PROG_BLUE,
                ).forEach { (c, code) ->
                    PressKey(onPress = { k(code) }, shape = RoundedCornerShape(50), color = c.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f).height(14.dp), label = "Touche couleur") {}
                }
            }

            PanelTabs(panel) { panel = it }
            when (panel) {
                Panel.Apps -> AppsPanel(vm)
                Panel.Digits -> DigitsPanel(k)
                Panel.Keyboard -> KeyboardPanel(vm)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(state: UiState, vm: RemoteActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val (dot, label) = when (state.connection) {
            Connection.Connected -> Palette.Ok to "Connectée"
            Connection.Connecting -> Palette.Yellow to "Connexion…"
            Connection.Disconnected -> Palette.Muted to "Déconnectée"
        }
        Column(Modifier.weight(1f)) {
            Text(state.tv?.name ?: "Télé", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(label, color = Palette.Muted, fontSize = 13.sp)
            }
        }
        IconButton(onClick = vm::changeTv) {
            Icon(Icons.Rounded.SwapHoriz, "Changer de télé", tint = Palette.Muted)
        }
    }
}

@Composable
private fun StatusBanner(state: UiState, vm: RemoteActions) {
    if (state.connection == Connection.Connected) return
    Surface(color = Palette.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.connection == Connection.Connecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Connexion à la télé…", Modifier.weight(1f))
            } else {
                Text(state.message ?: "Pas connecté à la télé.", Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = vm::connect) { Text("Réessayer") }
            }
        }
    }
}

@Composable
private fun VolumeLevel(state: UiState) {
    val v = state.volume ?: return
    if (v.max <= 0) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { v.level.toFloat() / v.max },
            modifier = Modifier.width(64.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (v.muted) Palette.Muted else Palette.Accent,
            trackColor = Palette.SurfaceHigh,
        )
        Text(if (v.muted) "Muet" else "${v.level}", color = Palette.Muted, fontSize = 12.sp)
    }
}

// ---- building blocks ---------------------------------------------------------

/**
 * A key that fires on touch-down (feels instant) with haptic feedback.
 * With [repeat], holding it keeps sending, like a real remote.
 */
@Composable
private fun PressKey(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = Palette.Surface,
    repeat: Boolean = false,
    label: String,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val action by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "press")

    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(if (pressed) Palette.SurfacePressed else color)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick { action(); true }
            }
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    action()
                    val repeater = if (repeat) scope.launch {
                        delay(400)
                        while (isActive) { action(); delay(110) }
                    } else null
                    waitForUpOrCancellation()
                    repeater?.cancel()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun RoundKey(
    icon: ImageVector,
    label: String,
    size: Dp = 60.dp,
    color: Color = Palette.Surface,
    repeat: Boolean = false,
    onPress: () -> Unit,
) {
    PressKey(onPress, Modifier.size(size), CircleShape, color, repeat, label) {
        Icon(icon, null, tint = if (color == Palette.Surface) Palette.OnSurface else Color.White)
    }
}

@Composable
private fun DPad(k: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth(0.82f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(Palette.Surface),
    ) {
        val arrow = Modifier.size(84.dp)
        PressKey({ k(KeyCodes.DPAD_UP) }, arrow.align(Alignment.TopCenter), CircleShape, Color.Transparent, true, "Haut") {
            Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.size(40.dp))
        }
        PressKey({ k(KeyCodes.DPAD_DOWN) }, arrow.align(Alignment.BottomCenter), CircleShape, Color.Transparent, true, "Bas") {
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(40.dp))
        }
        PressKey({ k(KeyCodes.DPAD_LEFT) }, arrow.align(Alignment.CenterStart), CircleShape, Color.Transparent, true, "Gauche") {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, Modifier.size(40.dp))
        }
        PressKey({ k(KeyCodes.DPAD_RIGHT) }, arrow.align(Alignment.CenterEnd), CircleShape, Color.Transparent, true, "Droite") {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(40.dp))
        }
        PressKey(
            { k(KeyCodes.DPAD_CENTER) },
            Modifier.fillMaxSize(0.42f).align(Alignment.Center), CircleShape, Palette.SurfaceHigh, false, "OK",
        ) {
            Text("OK", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun Rocker(label: String, modifier: Modifier, up: () -> Unit, down: () -> Unit) {
    Column(
        modifier.height(150.dp).clip(RoundedCornerShape(32.dp)).background(Palette.Surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PressKey(up, Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(32.dp), Color.Transparent, true, "$label +") {
            Icon(Icons.Rounded.Add, null)
        }
        Text(label, color = Palette.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        PressKey(down, Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(32.dp), Color.Transparent, true, "$label -") {
            Icon(Icons.Rounded.Remove, null)
        }
    }
}

@Composable
private fun PanelTabs(selected: Panel, onSelect: (Panel) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab(Icons.Rounded.Apps, "Applis", selected == Panel.Apps) { onSelect(Panel.Apps) }
        Tab(Icons.Rounded.Dialpad, "Chiffres", selected == Panel.Digits) { onSelect(Panel.Digits) }
        Tab(Icons.Rounded.Keyboard, "Clavier", selected == Panel.Keyboard) { onSelect(Panel.Keyboard) }
    }
}

@Composable
private fun RowScope.Tab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.SurfaceHigh else Color.Transparent),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (selected) Palette.OnSurface else Palette.Muted)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) Palette.OnSurface else Palette.Muted)
    }
}

@Composable
private fun ColumnScope.AppsPanel(vm: RemoteActions) {
    APPS.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (name, link) ->
                PressKey({ vm.openApp(link) }, Modifier.weight(1f).height(56.dp), RoundedCornerShape(14.dp), label = name) {
                    Text(name, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DigitsPanel(k: (Int) -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Guide", "0", "TV"))
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { label ->
                val code = when (label) {
                    "Guide" -> KeyCodes.GUIDE
                    "TV" -> KeyCodes.TV
                    else -> KeyCodes.digit(label.toInt())
                }
                PressKey({ k(code) }, Modifier.weight(1f).height(52.dp), RoundedCornerShape(14.dp), label = label) {
                    Text(label, fontSize = if (label.length == 1) 20.sp else 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.KeyboardPanel(vm: RemoteActions) {
    var text by rememberSaveable { mutableStateOf("") }
    Text(
        "Sélectionne d'abord un champ de texte sur la télé (recherche YouTube, mot de passe Wi-Fi…), " +
            "puis tape ici. Les lettres sont envoyées en minuscules.",
        color = Palette.Muted, fontSize = 13.sp,
    )
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Texte à envoyer") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { vm.typeText(text); text = "" }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PressKey({ vm.typeText(text); text = "" }, Modifier.weight(2f).height(52.dp), RoundedCornerShape(14.dp),
            Palette.Accent, label = "Envoyer") {
            Text("Envoyer", fontWeight = FontWeight.Medium, color = Color.White)
        }
        PressKey({ vm.key(KeyCodes.DEL) }, Modifier.weight(1f).height(52.dp), RoundedCornerShape(14.dp),
            repeat = true, label = "Effacer") {
            Icon(Icons.AutoMirrored.Rounded.Backspace, null)
        }
        PressKey({ vm.key(KeyCodes.ENTER) }, Modifier.weight(1f).height(52.dp), RoundedCornerShape(14.dp),
            label = "Entrée") {
            Icon(Icons.AutoMirrored.Rounded.KeyboardReturn, null)
        }
    }
}
