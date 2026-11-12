package com.mousikify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.mousikify.core.designsystem.theme.MousikifyTheme
import com.mousikify.core.model.DeepLinkCodec
import com.mousikify.core.model.DeepLinkPayload
import com.mousikify.core.scanner.work.MediaStoreScannerWorker
import com.mousikify.navigation.MainAppContainer
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<DeepLinkPayload?>(null)

    private val newIntentListener = Consumer<Intent> { newIntent ->
        setIntent(newIntent)
        DeepLinkCodec.decode(newIntent.dataString)?.let { payload ->
            pendingDeepLink = payload
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (audioGranted) {
            // Trigger library scan now that permissions are granted
            MediaStoreScannerWorker.enqueue(this, initialDelaySeconds = 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            pendingDeepLink = DeepLinkCodec.decode(intent?.dataString)
        }
        addOnNewIntentListener(newIntentListener)

        requestMediaPermissions()

        setContent {
            MousikifyTheme {
                MainAppContainer(
                    deepLinkPayload = pendingDeepLink,
                    onDeepLinkHandled = { pendingDeepLink = null }
                )
            }
        }
    }

    override fun onDestroy() {
        removeOnNewIntentListener(newIntentListener)
        super.onDestroy()
    }

    private fun requestMediaPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
