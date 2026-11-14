package com.example.p4

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var accessibilityStatusTextView: TextView
    private lateinit var writeSettingsStatusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val paddingPx = (16 * resources.displayMetrics.density).toInt()
        val spacingPx = (12 * resources.displayMetrics.density).toInt()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        accessibilityStatusTextView = TextView(this).apply {
            textSize = 16f
        }
        val accessibilityParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = spacingPx
        }

        writeSettingsStatusTextView = TextView(this).apply {
            textSize = 16f
        }
        val writeSettingsParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = spacingPx * 2
        }

        val btnAccessibility = Button(this).apply {
            text = getString(R.string.open_accessibility_settings)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val btnAccessibilityParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = spacingPx
        }

        val btnWriteSettings = Button(this).apply {
            text = getString(R.string.open_write_settings)
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        val btnWriteSettingsParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        rootLayout.addView(accessibilityStatusTextView, accessibilityParams)
        rootLayout.addView(writeSettingsStatusTextView, writeSettingsParams)
        rootLayout.addView(btnAccessibility, btnAccessibilityParams)
        rootLayout.addView(btnWriteSettings, btnWriteSettingsParams)

        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled()
        val isWriteSettingsGranted = Settings.System.canWrite(this)

        val accessibilityStatusText = if (isAccessibilityEnabled) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled)
        }

        val writeSettingsStatusText = if (isWriteSettingsGranted) {
            getString(R.string.status_granted)
        } else {
            getString(R.string.status_denied)
        }

        accessibilityStatusTextView.text = getString(
            R.string.accessibility_service_status,
            accessibilityStatusText
        )
        writeSettingsStatusTextView.text = getString(
            R.string.write_settings_status,
            writeSettingsStatusText
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val myComponent = ComponentName(this, KeyService::class.java)
        val shortName = myComponent.flattenToShortString()
        val fullName = myComponent.flattenToString()

        return enabledServices.split(":").any { service ->
            service.equals(shortName, ignoreCase = true) ||
                    service.equals(fullName, ignoreCase = true)
        }
    }
}
