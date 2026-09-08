package com.pinder.airhand

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pinder.airhand.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        binding.statusText.text = if (granted) {
            "Camera granted. Enable Accessibility, then tap Start."
        } else {
            "Camera permission is required."
        }
    }

    // Android 13+ requires this to actually show the "tracking active" foreground-service
    // notification. Tracking still works without it, but you won't get the on-screen reminder.
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: purely cosmetic, don't block Start on this */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.startButton.setOnClickListener { onStartClicked() }

        binding.stopButton.setOnClickListener {
            stopService(Intent(this, HandCameraService::class.java))
            GestureBus.publish(GestureEvent.Pause(true))
            binding.statusText.text = "Stopped."
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            !isAccessibilityServiceEnabled()
        ) {
            binding.statusText.text = "Camera granted. Enable Accessibility, then tap Start."
        }
    }

    private fun onStartClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        if (!isAccessibilityServiceEnabled()) {
            binding.statusText.text = "Enable AirHand under Accessibility first, or the cursor won't move."
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, HandCameraService::class.java))
        binding.statusText.text = "Hand control running. Switch to another app and keep your hand visible to the front camera."
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, AirHandAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        for (component in splitter) {
            if (component.equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
