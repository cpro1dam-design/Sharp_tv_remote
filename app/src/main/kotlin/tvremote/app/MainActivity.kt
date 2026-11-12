package tvremote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tvremote.app.ui.AppTheme
import tvremote.app.ui.PairingScreen
import tvremote.app.ui.RemoteScreen
import tvremote.app.ui.SetupScreen

class MainActivity : ComponentActivity() {

    private val vm: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = vm.onForeground()
            override fun onStop(owner: LifecycleOwner) = vm.onBackground()
        })

        setContent {
            AppTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                when (state.screen) {
                    Screen.Setup -> SetupScreen(state, vm)
                    Screen.Pairing -> PairingScreen(state, vm)
                    Screen.Remote -> RemoteScreen(state, vm)
                }
            }
        }
    }
}
