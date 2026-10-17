package com.project.lol.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Numerical references from the APK review, implemented independently in Compose. */
internal object NativeChromeMotion {
    const val PressMillis = 200
    const val EnterMillis = 150
    const val ExitMillis = 200
    val ButtonEasing = CubicBezierEasing(.3f, 0f, 0f, 1f)
    val SurfaceEasing = CubicBezierEasing(.3f, 0f, .4f, 1f)
    val SheetEasing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }
}

@Composable
internal fun Modifier.nativePressScale(interactions: MutableInteractionSource): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) .9f else 1f,
        animationSpec = tween(NativeChromeMotion.PressMillis, easing = NativeChromeMotion.ButtonEasing),
        label = "nativePressScale"
    )
    // Read the animated value in the layer, not while composing the player tree.
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * One vertical layer for entry, drag and dismissal. Audio stays completely outside.
 * The supplied dragHandle belongs on the header/artwork, never on the seek slider.
 */
@Composable
internal fun NativePlayerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable (close: () -> Unit, dragHandle: Modifier) -> Unit
) {
    // A restored open player is already on screen; do not replay entry on rotation.
    val position = remember { Animatable(if (visible) 0f else 1f) }
    var rendered by remember { mutableStateOf(visible) }
    var canDrag by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current.density
    val currentVisible = rememberUpdatedState(visible)
    val currentDismiss = rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val settleJob = remember { arrayOfNulls<Job>(1) }
    val gestureEpoch = remember { intArrayOf(0) }

    fun currentFraction(): Float = if (dragging) PlayerSheetPolicy.fraction(dragPx, heightPx) else position.value

    LaunchedEffect(visible) {
        gestureEpoch[0]++
        settleJob[0]?.cancel()
        canDrag = false
        if (visible) {
            val start = if (rendered) currentFraction() else 1f
            rendered = true
            position.snapTo(start)
            dragging = false
            if (position.value != 0f) {
                position.animateTo(0f, tween(NativeChromeMotion.EnterMillis, easing = NativeChromeMotion.SheetEasing))
            }
            canDrag = true
        } else if (rendered) {
            position.snapTo(currentFraction())
            dragging = false
            position.animateTo(1f, tween(NativeChromeMotion.ExitMillis, easing = NativeChromeMotion.SheetEasing))
            rendered = false
        }
    }

    if (!rendered) return

    fun finishDrag(velocity: Float, cancelled: Boolean) {
        if (!dragging || !currentVisible.value) return
        val fraction = currentFraction()
        val dismiss = PlayerSheetPolicy.shouldDismiss(dragPx, heightPx, velocity, density, cancelled)
        val epoch = ++gestureEpoch[0]
        settleJob[0]?.cancel()
        settleJob[0] = scope.launch {
            if (gestureEpoch[0] != epoch || !currentVisible.value) return@launch
            position.snapTo(fraction)
            // snapTo can suspend behind cancellation; an older release must not finish a newer drag.
            if (gestureEpoch[0] != epoch || !currentVisible.value) return@launch
            dragging = false
            if (dismiss) {
                currentDismiss.value()
            } else {
                position.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
            }
        }
    }

    val dragHandle = if (!visible || !canDrag) Modifier else Modifier.pointerInput(heightPx, density) {
        val tracker = VelocityTracker()
        var fingerTravel = 0f
        detectVerticalDragGestures(
            onDragStart = {
                val start = currentFraction()
                gestureEpoch[0]++
                settleJob[0]?.cancel()
                dragPx = start * heightPx
                dragging = true
                tracker.resetTracking()
                fingerTravel = 0f
            },
            onVerticalDrag = { change, delta ->
                if (dragging && currentVisible.value) {
                    // The handle itself moves: local coordinates would under-report velocity.
                    fingerTravel += delta
                    tracker.addPosition(change.uptimeMillis, Offset(0f, fingerTravel))
                    dragPx = PlayerSheetPolicy.clampOffset(dragPx + delta, heightPx)
                    change.consume()
                }
            },
            onDragEnd = {
                tracker.addPosition(android.os.SystemClock.uptimeMillis(), Offset(0f, fingerTravel))
                finishDrag(tracker.calculateVelocity().y, cancelled = false)
            },
            onDragCancel = { finishDrag(0f, cancelled = true) }
        )
    }

    Box(Modifier.fillMaxSize()) {
        // Keep the revealed area inert during a pull/exit. No dimming or blank button semantics.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
        Box(Modifier.fillMaxSize()
            .onSizeChanged { size ->
                if (dragging) dragPx = PlayerSheetPolicy.fraction(dragPx, heightPx) * size.height
                heightPx = size.height.toFloat()
            }
            .graphicsLayer { translationY = currentFraction() * size.height }
            .testTag("native-player-sheet")) {
            content({ if (currentVisible.value) currentDismiss.value() }, dragHandle)
        }
    }
}
