package com.veyro.p2p.features.remoteinput

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.veyro.p2p.protocol.RemoteInputCommand
import com.veyro.p2p.protocol.RemoteInputEvent

class VeyroAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cursorX = Float.NaN
    private var cursorY = Float.NaN
    private var pendingDeltaX = 0f
    private var pendingDeltaY = 0f
    private var moveScheduled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        resetCursor()
        RemoteInputBridge.attach(this)
    }

    override fun onDestroy() {
        RemoteInputBridge.detach(this)
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    internal fun handleRemoteInput(event: RemoteInputEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return when (event.inputCommand) {
            RemoteInputCommand.MOUSE_DELTA -> {
                queueMouseDelta(event.deltaAxisX, event.deltaAxisY)
                true
            }

            RemoteInputCommand.SINGLE_TAP -> dispatchTap(doubleTap = false)
            RemoteInputCommand.DOUBLE_TAP -> dispatchTap(doubleTap = true)
            RemoteInputCommand.SCROLL_GESTURE -> dispatchScroll(
                event.deltaAxisX,
                event.deltaAxisY
            )

            RemoteInputCommand.KEYBOARD_INPUT -> insertTextOrGlobalAction(event.keyboardChar)
            RemoteInputCommand.REMOTE_INPUT_COMMAND_UNKNOWN,
            RemoteInputCommand.UNRECOGNIZED -> false
        }
    }

    private fun queueMouseDelta(deltaX: Float, deltaY: Float) {
        pendingDeltaX += deltaX.coerceIn(-500f, 500f)
        pendingDeltaY += deltaY.coerceIn(-500f, 500f)
        if (moveScheduled) return
        moveScheduled = true
        mainHandler.postDelayed({
            ensureCursor()
            cursorX = (cursorX + pendingDeltaX).coerceIn(0f, screenWidth())
            cursorY = (cursorY + pendingDeltaY).coerceIn(0f, screenHeight())
            pendingDeltaX = 0f
            pendingDeltaY = 0f
            moveScheduled = false
        }, FRAME_BATCH_MILLIS)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun dispatchTap(doubleTap: Boolean): Boolean {
        ensureCursor()
        val path = Path().apply { moveTo(cursorX, cursorY) }
        val builder = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MILLIS)
        )
        if (doubleTap) {
            val secondPath = Path().apply { moveTo(cursorX, cursorY) }
            builder.addStroke(
                GestureDescription.StrokeDescription(
                    secondPath,
                    DOUBLE_TAP_DELAY_MILLIS,
                    TAP_DURATION_MILLIS
                )
            )
        }
        return dispatchGesture(builder.build(), null, null)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun dispatchScroll(deltaX: Float, deltaY: Float): Boolean {
        ensureCursor()
        val effectiveX = deltaX.takeUnless { it == 0f } ?: 0f
        val effectiveY = deltaY.takeUnless { it == 0f } ?: DEFAULT_SCROLL_DISTANCE
        val endX = (cursorX - effectiveX).coerceIn(0f, screenWidth())
        val endY = (cursorY - effectiveY).coerceIn(0f, screenHeight())
        val path = Path().apply {
            moveTo(cursorX, cursorY)
            lineTo(endX, endY)
        }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, SCROLL_DURATION_MILLIS))
                .build(),
            null,
            null
        )
    }

    private fun insertTextOrGlobalAction(value: String): Boolean {
        return when (value) {
            TOKEN_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            TOKEN_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            TOKEN_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            else -> setFocusedText(value.take(MAX_KEYBOARD_CHUNK))
        }
    }

    private fun setFocusedText(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return try {
            if (!focused.isEditable) return false
            val current = focused.text?.toString().orEmpty()
            val updated = when (value) {
                TOKEN_BACKSPACE -> current.dropLast(1)
                else -> (current + value).take(MAX_EDITABLE_TEXT_LENGTH)
            }
            focused.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        updated
                    )
                }
            )
        } finally {
            focused.recycle()
        }
    }

    private fun resetCursor() {
        cursorX = screenWidth() / 2f
        cursorY = screenHeight() / 2f
    }

    private fun ensureCursor() {
        if (cursorX.isNaN() || cursorY.isNaN()) resetCursor()
    }

    private fun screenWidth(): Float = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)

    private fun screenHeight(): Float = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

    companion object {
        const val TOKEN_BACK = "{BACK}"
        const val TOKEN_HOME = "{HOME}"
        const val TOKEN_RECENTS = "{RECENTS}"
        const val TOKEN_BACKSPACE = "{BACKSPACE}"

        private const val FRAME_BATCH_MILLIS = 16L
        private const val TAP_DURATION_MILLIS = 60L
        private const val DOUBLE_TAP_DELAY_MILLIS = 140L
        private const val SCROLL_DURATION_MILLIS = 220L
        private const val DEFAULT_SCROLL_DISTANCE = 420f
        private const val MAX_KEYBOARD_CHUNK = 64
        private const val MAX_EDITABLE_TEXT_LENGTH = 20_000
    }
}
