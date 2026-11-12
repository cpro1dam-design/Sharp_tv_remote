package tvremote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tvremote.app.RemoteActions
import tvremote.app.UiState

@Composable
private fun Page(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Palette.Background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun ErrorText(message: String?) {
    if (message != null) {
        Surface(color = Palette.Power.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
            Text(message, Modifier.padding(12.dp), color = Palette.OnSurface)
        }
    }
}

@Composable
fun SetupScreen(state: UiState, vm: RemoteActions) {
    var ip by remember { mutableStateOf("") }
    Page {
        Text("Choisis ta télé", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Le téléphone et la télé doivent être allumés et connectés au même Wi-Fi.",
            color = Palette.Muted,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Télés trouvées", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (state.searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (state.found.isEmpty()) {
            Text(
                if (state.searching) "Recherche en cours…" else "Aucune télé trouvée pour l'instant.",
                color = Palette.Muted,
            )
        }
        state.found.forEach { tv ->
            Surface(
                color = Palette.Surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clickable { vm.chooseTv(tv.name, tv.host) },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tv, null, tint = Palette.Accent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tv.name, fontWeight = FontWeight.Medium)
                        Text(tv.host, color = Palette.Muted, fontSize = 13.sp)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Palette.Muted)
                }
            }
        }
        if (!state.searching) {
            OutlinedButton(onClick = vm::startSearch) { Text("Rechercher à nouveau") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Ou entre l'adresse IP de la télé", fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = ip,
            onValueChange = { v -> ip = v.filter { it.isDigit() || it == '.' } },
            label = { Text("Adresse IP (ex. 192.168.1.42)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { vm.chooseTv("", ip) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.chooseTv("", ip) }, enabled = ip.count { it == '.' } == 3) { Text("Se connecter") }
        Text(
            "Pour trouver l'adresse sur la télé : Paramètres → Réseau et Internet → ton réseau Wi-Fi → Adresse IP.",
            color = Palette.Muted, fontSize = 13.sp,
        )
        ErrorText(state.message)
    }
}

@Composable
fun PairingScreen(state: UiState, vm: RemoteActions) {
    var code by remember { mutableStateOf("") }
    Page {
        Text("Appairage", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Première connexion à « ${state.tv?.name ?: "la télé"} » : un code à 6 caractères " +
                "s'affiche sur l'écran de la télé. Recopie-le ici.",
            color = Palette.Muted,
        )
        when {
            state.pairingBusy && !state.pairingReady -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Demande du code à la télé…")
            }
            state.pairingReady -> {
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                    },
                    label = { Text("Code affiché sur la télé") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace, letterSpacing = 6.sp, textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (code.length == 6) vm.submitCode(code) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.submitCode(code) },
                    enabled = code.length == 6 && !state.pairingBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.pairingBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Valider")
                }
            }
            else -> Button(onClick = vm::retryPairing) { Text("Réessayer") }
        }
        ErrorText(state.message)
        TextButton(onClick = vm::backToSetup) { Text("Choisir une autre télé") }
    }
}
