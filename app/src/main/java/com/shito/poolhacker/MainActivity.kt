package com.shito.poolhacker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(32, 32, 32, 32) }
        layout.addView(TextView(this).apply { text = "SHITO POOL HACKER\nخطوط تصويب مساعدة"; textSize = 24f; gravity = Gravity.CENTER })
        layout.addView(Button(this).apply { text = "السماح بالظهور فوق التطبيقات"; setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } })
        layout.addView(Button(this).apply { text = "تشغيل خطوط التصويب"; setOnClickListener { if (Settings.canDrawOverlays(this@MainActivity)) startService(Intent(this@MainActivity, AimOverlayService::class.java)) } })
        layout.addView(Button(this).apply { text = "إيقاف الخطوط"; setOnClickListener { stopService(Intent(this@MainActivity, AimOverlayService::class.java)) } })
        layout.addView(Button(this).apply { text = "فتح 8 Ball Pool"; setOnClickListener { val launch = packageManager.getLaunchIntentForPackage("com.miniclip.eightballpool"); if (launch != null) startActivity(launch) } })
        setContentView(layout)
    }
}
