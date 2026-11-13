package tvremote.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tvremote.app.APP_SLOTS
import tvremote.app.Connection
import tvremote.app.RemoteActions
import tvremote.app.UiState
import tvremote.core.KeyCodes
import kotlin.math.min

/**
 * Size of the content the remote is designed for, in dp. The whole remote is
 * scaled by `min(available / design)` so it always fits on one screen without
 * scrolling, on small phones as on large ones.
 */
private const val DESIGN_WIDTH = 328f
private const val DESIGN_HEIGHT = 584f

/** Height of everything but the D-pad, gaps included, in design dp. */
private const val OTHER_ROWS_HEIGHT = 374f
private const val DPAD_WIDTH_RATIO = 0.64f

private val LocalScale = compositionLocalOf { 1f }

/** A design-time dp value, scaled to the current screen. */
private val Number.u: Dp @Composable get() = (toFloat() * LocalScale.current).dp

/** A design-time text size, scaled and immune to the system font size (so it always fits). */
private val Number.t: TextUnit
    @Composable get() = (toFloat() * LocalScale.current / LocalDensity.current.fontScale).sp

private enum class Sheet { None, Digits, Keyboard }

@Composable
fun RemoteScreen(state: UiState, vm: RemoteActions) {
    var sheet by rememberSaveable { mutableStateOf(Sheet.None) }
    val k: (Int) -> Unit = vm::key

    Surface(Modifier.fillMaxSize(), color = Palette.Background) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val scale = min(maxWidth.value / DESIGN_WIDTH, maxHeight.value / DESIGN_HEIGHT).coerceIn(0.55f, 1.6f)
            // D-pad: at most 64 % of the width, and never more than the height left by the other rows.
            val dpad = min(maxWidth.value * DPAD_WIDTH_RATIO, maxHeight.value - OTHER_ROWS_HEIGHT * scale).dp
            CompositionLocalProvider(LocalScale provides scale) {
                Column(
                    Modifier.fillMaxSize(),
                    // Spare height is shared evenly between the rows.
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Header(state, vm, k)

                    // Search and settings on either side of the D-pad.
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundKey(Icons.Rounded.Search, "Recherche", 46) { k(KeyCodes.SEARCH) }
                        DPad(dpad, k)
                        RoundKey(Icons.Rounded.Settings, "Paramètres", 46) { k(KeyCodes.SETTINGS) }
                    }

                    // Home in the middle, back right next to it.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.u, Alignment.CenterHorizontally)) {
                        RoundKey(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", 54) { k(KeyCodes.BACK) }
                        RoundKey(Icons.Rounded.Home, "Accueil", 54) { k(KeyCodes.HOME) }
                        RoundKey(Icons.Rounded.Menu, "Menu", 54) { k(KeyCodes.MENU) }
                    }

                    // Volume in the middle, app shortcuts on each side.
                    Row(Modifier.fillMaxWidth().height(118.u), horizontalArrangement = Arrangement.spacedBy(14.u)) {
                        AppColumn(APP_SLOTS.indices.take(2), vm, Modifier.weight(1f))
                        val v = state.volume
                        val level = when {
                            v == null || v.max <= 0 -> null
                            v.muted -> "Muet"
                            else -> "${v.level}"
                        }
                        Rocker("VOL", level, Modifier.width(62.u), up = { k(KeyCodes.VOLUME_UP) }, down = { k(KeyCodes.VOLUME_DOWN) })
                        AppColumn(APP_SLOTS.indices.drop(2), vm, Modifier.weight(1f))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        RoundKey(Icons.Rounded.SkipPrevious, "Précédent", 46) { k(KeyCodes.MEDIA_PREVIOUS) }
                        RoundKey(Icons.Rounded.FastRewind, "Retour rapide", 46, repeat = true) { k(KeyCodes.MEDIA_REWIND) }
                        RoundKey(Icons.Rounded.PlayArrow, "Lecture / pause", 46) { k(KeyCodes.MEDIA_PLAY_PAUSE) }
                        RoundKey(Icons.Rounded.FastForward, "Avance rapide", 46, repeat = true) { k(KeyCodes.MEDIA_FAST_FORWARD) }
                        RoundKey(Icons.Rounded.SkipNext, "Suivant", 46) { k(KeyCodes.MEDIA_NEXT) }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.u, Alignment.CenterHorizontally)) {
                        RoundKey(Icons.AutoMirrored.Rounded.VolumeOff, "Couper le son", 50) { k(KeyCodes.VOLUME_MUTE) }
                        RoundKey(Icons.Rounded.Dialpad, "Pavé numérique", 50) { sheet = Sheet.Digits }
                        RoundKey(Icons.Rounded.Keyboard, "Clavier", 50) { sheet = Sheet.Keyboard }
                    }
                }

                StatusBanner(state, vm, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    when (sheet) {
        Sheet.Digits -> DigitsSheet(k) { sheet = Sheet.None }
        Sheet.Keyboard -> KeyboardSheet(vm) { sheet = Sheet.None }
        Sheet.None -> {}
    }
}

/**
 * Top row: power, the TV name and connection status, source.
 * Tapping the TV name offers to switch to another TV.
 */
@Composable
private fun Header(state: UiState, vm: RemoteActions, k: (Int) -> Unit) {
    var confirmChange by remember { mutableStateOf(false) }
    val name = state.tv?.name ?: "Télé"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundKey(Icons.Rounded.PowerSettingsNew, "Marche / arrêt", 46, Palette.Power) { k(KeyCodes.POWER) }
        Spacer(Modifier.width(10.u))
        val (dot, label) = when (state.connection) {
            Connection.Connected -> Palette.Ok to "Connectée"
            Connection.Connecting -> Palette.Yellow to "Connexion…"
            Connection.Disconnected -> Palette.Muted to "Déconnectée"
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.u))
                .clickable(onClickLabel = "Changer de télé") { confirmChange = true }
                .padding(vertical = 4.u),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.SemiBold, fontSize = 15.t, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(20.u), tint = Palette.Muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.u).clip(CircleShape).background(dot))
                Spacer(Modifier.width(5.u))
                Text(label, color = Palette.Muted, fontSize = 11.t)
            }
        }
        Spacer(Modifier.width(10.u))
        RoundKey(Icons.AutoMirrored.Rounded.Input, "Source", 46) { k(KeyCodes.TV_INPUT) }
    }

    if (confirmChange) {
        AlertDialog(
            onDismissRequest = { confirmChange = false },
            title = { Text("Changer de télé ?") },
            text = { Text("La télécommande va se déconnecter de « $name » et chercher les télés sur le Wi-Fi.") },
            confirmButton = {
                TextButton(onClick = { confirmChange = false; vm.changeTv() }) { Text("Changer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmChange = false }) { Text("Annuler") }
            },
            containerColor = Palette.Surface,
        )
    }
}

/** Floats over the bottom of the remote, so the layout never moves. */
@Composable
private fun StatusBanner(state: UiState, vm: RemoteActions, modifier: Modifier) {
    if (state.connection == Connection.Connected) return
    Surface(
        color = Palette.SurfaceHigh,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.connection == Connection.Connecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Connexion à la télé…", Modifier.weight(1f), fontSize = 14.sp)
            } else {
                Text(state.message ?: "Pas connecté à la télé.", Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = vm::connect) { Text("Réessayer") }
            }
        }
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
    size: Int,
    color: Color = Palette.Surface,
    repeat: Boolean = false,
    onPress: () -> Unit,
) {
    PressKey(onPress, Modifier.size(size.u), CircleShape, color, repeat, label) {
        Icon(icon, null, Modifier.size((size * 0.46f).u),
            tint = if (color == Palette.Surface) Palette.OnSurface else Color.White)
    }
}

@Composable
private fun DPad(diameter: Dp, k: (Int) -> Unit) {
    Box(Modifier.size(diameter).clip(CircleShape).background(Palette.Surface)) {
        val arrow = Modifier.size(diameter * 0.32f)
        val icon = Modifier.size(diameter * 0.16f)
        PressKey({ k(KeyCodes.DPAD_UP) }, arrow.align(Alignment.TopCenter), CircleShape, Color.Transparent, true, "Haut") {
            Icon(Icons.Rounded.KeyboardArrowUp, null, icon)
        }
        PressKey({ k(KeyCodes.DPAD_DOWN) }, arrow.align(Alignment.BottomCenter), CircleShape, Color.Transparent, true, "Bas") {
            Icon(Icons.Rounded.KeyboardArrowDown, null, icon)
        }
        PressKey({ k(KeyCodes.DPAD_LEFT) }, arrow.align(Alignment.CenterStart), CircleShape, Color.Transparent, true, "Gauche") {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, icon)
        }
        PressKey({ k(KeyCodes.DPAD_RIGHT) }, arrow.align(Alignment.CenterEnd), CircleShape, Color.Transparent, true, "Droite") {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, icon)
        }
        PressKey(
            { k(KeyCodes.DPAD_CENTER) },
            Modifier.size(diameter * 0.42f).align(Alignment.Center), CircleShape, Palette.SurfaceHigh, false, "OK",
        ) {
            val fontScale = LocalDensity.current.fontScale
            Text("OK", fontWeight = FontWeight.Bold, fontSize = (diameter.value * 0.085f / fontScale).sp)
        }
    }
}

@Composable
private fun Rocker(label: String, detail: String?, modifier: Modifier, up: () -> Unit, down: () -> Unit) {
    Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(30.u)).background(Palette.Surface),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PressKey(up, Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(30.u), Color.Transparent, true, "$label +") {
            Icon(Icons.Rounded.Add, null, Modifier.size(24.u))
        }
        Text(label, color = Palette.Muted, fontSize = 12.t, fontWeight = FontWeight.SemiBold)
        if (detail != null) Text(detail, color = Palette.Accent, fontSize = 11.t)
        PressKey(down, Modifier.fillMaxWidth().weight(1f), RoundedCornerShape(30.u), Color.Transparent, true, "$label -") {
            Icon(Icons.Rounded.Remove, null, Modifier.size(24.u))
        }
    }
}

@Composable
private fun AppColumn(slots: List<Int>, vm: RemoteActions, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.u)) {
        slots.forEach { i ->
            AppKey(APP_SLOTS[i].name, Modifier.fillMaxWidth().weight(1f),
                onClick = { vm.openApp(i) }, onLongClick = { vm.bindAppToCurrent(i) })
        }
    }
}

/** Tap opens the app; long press binds the button to the app currently open on the TV. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppKey(name: String, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .clip(RoundedCornerShape(12.u))
            .background(Palette.Surface)
            .combinedClickable(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
                onLongClickLabel = "Associer à l'appli ouverte sur la télé",
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(name, fontWeight = FontWeight.Medium, fontSize = 13.t, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- sheets -------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DigitsSheet(k: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Guide", "0", "TV"))
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { label ->
                        val code = when (label) {
                            "Guide" -> KeyCodes.GUIDE
                            "TV" -> KeyCodes.TV
                            else -> KeyCodes.digit(label.toInt())
                        }
                        PressKey({ k(code) }, Modifier.weight(1f).height(56.dp), RoundedCornerShape(14.dp), label = label) {
                            Text(label, fontSize = if (label.length == 1) 22.sp else 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardSheet(vm: RemoteActions, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sélectionne d'abord un champ de texte sur la télé (recherche, mot de passe Wi-Fi…), " +
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
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
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
    }
}
