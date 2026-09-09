package dev.backbee.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Watches the audio route.
 *
 * Two things happen when the car turns off: Bluetooth disconnects, and the app
 * is very likely to be killed shortly after. Flushing on the disconnect itself,
 * rather than waiting for a pause callback that may never be delivered, is what
 * keeps the position.
 *
 * The reverse - connecting to the car - optionally starts playback, but only if
 * the user asked for it. Default off: instantly ready, never surprising.
 */
class AudioRouteMonitor(
    context: Context,
    private val onRouteLost: () -> Unit,
    private val onBluetoothConnected: () -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.isExternalOutput }) {
                Log.d(TAG, "External output removed; flushing position")
                onRouteLost()
            }
        }

        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            // Registering replays the devices already present as one "added"
            // batch. Treating that as the car just connecting would start
            // playback merely because the service was created - by the widget,
            // by Auto browsing - while Bluetooth happened to be on.
            if (SystemClock.elapsedRealtime() - startedAt < INITIAL_REPLAY_WINDOW_MS) return
            if (added.any { it.isBluetoothOutput }) {
                Log.d(TAG, "Bluetooth output connected")
                onBluetoothConnected()
            }
        }
    }

    fun start() {
        startedAt = SystemClock.elapsedRealtime()
        audioManager.registerAudioDeviceCallback(callback, handler)
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private val AudioDeviceInfo.isBluetoothOutput: Boolean
        get() = isSink && (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)

    private val AudioDeviceInfo.isExternalOutput: Boolean
        get() = isSink && when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_AUX_LINE,
            -> true

            else -> false
        }

    companion object {
        private const val TAG = "AudioRouteMonitor"
        private const val INITIAL_REPLAY_WINDOW_MS = 1_500L
    }
}
