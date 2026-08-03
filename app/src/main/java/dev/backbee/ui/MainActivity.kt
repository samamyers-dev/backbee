package dev.backbee.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.backbee.BackbeeApp
import dev.backbee.playback.PlayerConnection
import dev.backbee.ui.theme.BackbeeTheme

class MainActivity : ComponentActivity() {

    private lateinit var playerConnection: PlayerConnection

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as BackbeeApp).container
        playerConnection = PlayerConnection(applicationContext, lifecycleScope)

        askForNotificationsIfNeeded()

        setContent {
            BackbeeTheme {
                BackbeeNavHost(
                    container = container,
                    player = playerConnection,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }

    override fun onStop() {
        playerConnection.release()
        super.onStop()
    }

    /**
     * The media notification is one of the app's main surfaces, so it is worth
     * asking. Declining costs the notification and nothing else - playback,
     * downloads and Auto all carry on.
     */
    private fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
