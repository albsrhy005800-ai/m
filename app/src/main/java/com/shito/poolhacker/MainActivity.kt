package com.shito.poolhacker

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val poolPackage = "com.miniclip.eightballpool"
    private var waitingForOverlayPermission = false
    private val captureRequest = 4102
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(Color.rgb(9, 18, 17))
        }

        val title = TextView(this).apply {
            text = "SHITO POOL"
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "اختر لعبتك لبدء مساعد التصويب"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(28))
        }
        root.addView(subtitle, matchWrap())

        val section = TextView(this).apply {
            text = "الألعاب"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
        }
        root.addView(section, matchWrap())

        val gameCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(Color.rgb(20, 48, 39), dp(16))
        }
        val gameName = TextView(this).apply {
            text = "🎱  8 Ball Pool"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
        }
        gameCard.addView(gameName, matchWrap())
        val gameInfo = TextView(this).apply {
            text = "مساعد خطوط التصويب"
            textSize = 14f
            setTextColor(Color.rgb(190, 220, 205))
            gravity = Gravity.RIGHT
            setPadding(0, dp(8), 0, 0)
        }
        gameCard.addView(gameInfo, matchWrap())
        val cardParams = matchWrap()
        cardParams.topMargin = dp(12)
        root.addView(gameCard, cardParams)

        val openButton = Button(this).apply {
            text = "فتح اللعبة وتشغيل الخطوط"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(25, 150, 95), dp(12))
            setOnClickListener { beginCapture() }
        }
        val buttonParams = matchWrap()
        buttonParams.topMargin = dp(24)
        root.addView(openButton, buttonParams)

        val permissionButton = Button(this).apply {
            text = "إعداد صلاحية الظهور فوق اللعبة"
            textSize = 14f
            isAllCaps = false
            setOnClickListener {
                waitingForOverlayPermission = false
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        root.addView(permissionButton, matchWrap())

        val stopButton = Button(this).apply {
            text = "إيقاف التعرف والخطوط"
            isAllCaps = false
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                stopService(Intent(this@MainActivity, AimOverlayService::class.java))
                statusText.text = "تم إيقاف التعرف والخطوط"
            }
        }
        root.addView(stopButton, matchWrap())

        statusText = TextView(this).apply {
            text = "اختر 8 Ball Pool ثم اضغط فتح اللعبة"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        root.addView(statusText, matchWrap())
        setContentView(root)
    }

    private fun beginCapture() {
        val launch = packageManager.getLaunchIntentForPackage(poolPackage)
        if (launch == null) {
            Toast.makeText(this, "لم يتم العثور على 8 Ball Pool.", Toast.LENGTH_LONG).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "فعّل الظهور فوق التطبيقات ثم ارجع", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("Deprecated API retained for minimum SDK compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequest) return
        if (resultCode != RESULT_OK || data == null) {
            statusText.text = "لم تتم الموافقة على مشاركة الشاشة"
            return
        }
        val capture = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(capture)
        } else {
            // Android 7.1 and older do not provide startForegroundService().
            startService(capture)
        }
        startService(Intent(this, AimOverlayService::class.java))
        statusText.text = "بدأ تحليل الشاشة؛ افتح مباراة داخل اللعبة"
        packageManager.getLaunchIntentForPackage(poolPackage)?.let { startActivity(it) }
    }

    override fun onResume() {
        super.onResume()
        if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false
            beginCapture()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
}
