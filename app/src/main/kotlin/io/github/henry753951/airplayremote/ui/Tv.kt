package io.github.henry753951.airplayremote.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

fun Context.isTvDevice(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType ==
        Configuration.UI_MODE_TYPE_TELEVISION

@Composable
fun isTv(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.isTvDevice() }
}

fun Modifier.dpadFocus(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val tv = isTv()
    onFocusChanged { focused = it.isFocused }
        // on a TV across the room a thin outline is easy to lose; add a light wash over the item
        .thenIf(tv && focused) {
            drawWithContent {
                drawContent()
                drawOutline(shape.createOutline(size, layoutDirection, this), Color.White.copy(alpha = 0.12f))
            }
        }
        .border(if (tv) 3.dp else 2.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
}

// remote focus lands on the gate, not the field, so the keyboard only opens on OK
@Composable
fun TvEditGate(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    if (!isTv()) {
        content(modifier)
        return
    }
    var editing by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing) field.requestFocusUntilLanded() }
    Box(
        modifier
            .dpadFocus()
            .clickable(remember { MutableInteractionSource() }, indication = null) { editing = true }
    ) {
        content(
            Modifier
                .focusRequester(field)
                .focusProperties { canFocus = editing }
                .onFocusChanged {
                    if (it.isFocused) {
                        fieldFocused = true
                    } else if (fieldFocused) {
                        fieldFocused = false
                        editing = false
                    }
                }
        )
    }
}

fun Modifier.dpadAdjust(onLeft: () -> Unit, onRight: () -> Unit): Modifier = composed {
    val focus = LocalFocusManager.current
    onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) {
            Key.DirectionLeft -> { onLeft(); true }
            Key.DirectionRight -> { onRight(); true }
            // slider
            Key.DirectionUp -> { focus.moveFocus(FocusDirection.Up); true }
            Key.DirectionDown -> { focus.moveFocus(FocusDirection.Down); true }
            else -> false
        }
    }
}
