package com.project.lol.islandbridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class BridgeSetupActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(this).apply {
            text = "Spotilol Island Bridge"
            textSize = 24f
            gravity = Gravity.CENTER
        }

        val info = TextView(this).apply {
            text = "Experimental Vivo Origin Island bridge.\n\n1. Allow notifications.\n2. Grant Notification Access.\n3. Start Spotilol and play a track.\n\nThe bridge listens only to com.project.lol media notifications."
            textSize = 16f
            setPadding(0, padding, 0, padding)
        }

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, padding)
        }

        val accessButton = Button(this).apply {
            text = "Grant Notification Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        val testButton = Button(this).apply {
            text = "Test Origin Island"
            setOnClickListener {
                BridgeNotificationCaster.postTest(this@BridgeSetupActivity)
            }
        }

        val cancelButton = Button(this).apply {
            text = "Clear Test / Island"
            setOnClickListener {
                BridgeNotificationCaster.cancel(this@BridgeSetupActivity)
            }
        }

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        root.addView(title, lp)
        root.addView(info, lp)
        root.addView(status, lp)
        root.addView(accessButton, lp)
        root.addView(testButton, lp)
        root.addView(cancelButton, lp)
        setContentView(root)

        requestNotificationPermissionIfNeeded()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val notificationAllowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        val listenerAllowed =
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

        status.text = buildString {
            append("Package: ")
            append(packageName)
            append("\nNotifications: ")
            append(if (notificationAllowed) "allowed" else "NOT ALLOWED")
            append("\nNotification Access: ")
            append(if (listenerAllowed) "granted" else "NOT GRANTED")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
