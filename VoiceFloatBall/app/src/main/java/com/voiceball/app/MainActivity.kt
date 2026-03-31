package com.voiceball.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupUI() {
        findViewById<Button>(R.id.btn_grant_overlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btn_grant_mic).setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }

        findViewById<Button>(R.id.btn_grant_accessibility).setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            if (allPermissionsGranted()) {
                startFloatingBall()
            } else {
                updateStatus()
            }
        }

        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopService(Intent(this, FloatingBallService::class.java))
            updateStatus()
        }
    }

    private fun updateStatus() {
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val micOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val accessibilityOk = isAccessibilityEnabled()

        fun statusText(ok: Boolean) = if (ok) "✅" else "❌ 点击授权"

        findViewById<TextView>(R.id.tv_status_overlay).text = "悬浮窗权限 ${statusText(overlayOk)}"
        findViewById<TextView>(R.id.tv_status_mic).text = "录音权限 ${statusText(micOk)}"
        findViewById<TextView>(R.id.tv_status_accessibility).text = "无障碍服务 ${statusText(accessibilityOk)}"

        val btnStart = findViewById<Button>(R.id.btn_start)
        val btnStop = findViewById<Button>(R.id.btn_stop)

        if (FloatingBallService.isRunning) {
            btnStart.isEnabled = false
            btnStart.text = "运行中"
            btnStop.isEnabled = true
        } else {
            btnStart.isEnabled = allPermissionsGranted()
            btnStart.text = "启动悬浮球"
            btnStop.isEnabled = false
        }
    }

    private fun allPermissionsGranted(): Boolean {
        val overlayOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        val micOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // 无障碍是可选的（降级到剪贴板），不强制
        return overlayOk && micOk
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName)
    }

    private fun startFloatingBall() {
        val intent = Intent(this, FloatingBallService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus()
        // 启动后可以最小化
        moveTaskToBack(true)
    }
}
