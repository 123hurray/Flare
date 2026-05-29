package dev.dimension.flare.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

internal fun Modifier.threeFingerSwipeLeft(onSwipeLeft: () -> Unit): Modifier =
    threeFingerSwipeHorizontal(
        onSwipeLeft = onSwipeLeft,
    )

internal fun Modifier.threeFingerSwipeRight(onSwipeRight: () -> Unit): Modifier =
    threeFingerSwipeHorizontal(
        onSwipeRight = onSwipeRight,
    )

internal fun Modifier.threeFingerSwipeHorizontal(
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
): Modifier =
    pointerInput(onSwipeLeft, onSwipeRight) {
        awaitEachGesture {
            var dragX = 0f
            var dragY = 0f
            var tracking = false
            var triggered = false
            val threshold = 80.dp.toPx()
            val crossAxisSlop = 48.dp.toPx()
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 3) {
                    tracking = true
                    dragX +=
                        pressed
                            .take(3)
                            .map { it.positionChange().x }
                            .average()
                            .toFloat()
                    dragY +=
                        pressed
                            .take(3)
                            .map { it.positionChange().y }
                            .average()
                            .toFloat()
                    pressed.forEach { it.consume() }
                }
                if (!tracking || triggered || kotlin.math.abs(dragY) > crossAxisSlop) {
                    continue
                }
                if (dragX <= -threshold) {
                    triggered = true
                    onSwipeLeft()
                    break
                }
                if (dragX >= threshold) {
                    triggered = true
                    onSwipeRight()
                    break
                }
            }
        }
    }
