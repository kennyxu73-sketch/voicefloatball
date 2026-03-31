package com.voiceball.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var ballImage: ImageView
    private lateinit var params: WindowManager.LayoutParams

    // 状态提示悬浮窗
    private var statusView: View? = null
    private var tvStatus: TextView? = null

    private lateinit var speechRecognizer: SpeechRecognizer
    private val handler = Handler(Looper.getMainLooper())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false
    private var isListening = false
    private val startRecordRunnable = Runnable {
        if (!isMoving) {
            vibrate()
            startVoiceRecognition()
        }
    }

    companion object {
        const val CHANNEL_ID = "VoiceBallChannel"
        const val NOTIFICATION_ID = 1001
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        setupFloatingBall()
        setupStatusView()
        setupSpeechRecognizer()
    }

    private fun setupFloatingBall() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_ball, null)
        ballImage = floatingView.findViewById(R.id.iv_ball)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        windowManager.addView(floatingView, params)

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    // 按下超过 200ms 且没有移动，则开始录音
                    handler.postDelayed(startRecordRunnable, 200)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isMoving = true
                        // 移动时取消录音启动
                        handler.removeCallbacks(startRecordRunnable)
                        if (isListening) stopListening()
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(startRecordRunnable)
                    if (!isMoving && isListening) {
                        // 松手 → 停止录音，触发识别
                        vibrate()
                        stopListening()
                    } else if (!isMoving && !isListening) {
                        // 点击太快（未到200ms），也触发一次录音再立即停
                        // 什么都不做，让用户感受到需要按住
                        showStatus("按住说话")
                        handler.postDelayed({ hideStatus() }, 1200)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupStatusView() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        statusView = LayoutInflater.from(this).inflate(R.layout.layout_status_toast, null)
        tvStatus = statusView?.findViewById(R.id.tv_status)

        val statusParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 200
        }

        statusView?.visibility = View.GONE
        windowManager.addView(statusView, statusParams)
    }

    private fun showStatus(text: String) {
        handler.post {
            tvStatus?.text = text
            statusView?.visibility = View.VISIBLE
        }
    }

    private fun hideStatus() {
        handler.post {
            statusView?.visibility = View.GONE
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                showStatus("请说话...")
                handler.post { ballImage.setImageResource(R.drawable.ic_mic_active) }
            }

            override fun onBeginningOfSpeech() {
                showStatus("识别中...")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                showStatus("处理中...")
            }

            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "录音错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误，请检查连接"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到内容"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器繁忙，请稍后"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "等待超时"
                    else -> "识别失败($error)"
                }
                showStatus(msg)
                handler.post { ballImage.setImageResource(R.drawable.ic_mic) }
                handler.postDelayed({ hideStatus() }, 2000)
                // 重建识别器
                handler.postDelayed({ rebuildRecognizer() }, 500)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()

                handler.post { ballImage.setImageResource(R.drawable.ic_mic) }

                if (!text.isNullOrEmpty()) {
                    showStatus("\"$text\"")
                    VoiceAccessibilityService.instance?.insertText(text)
                    handler.postDelayed({ hideStatus() }, 1500)
                } else {
                    showStatus("未识别到内容")
                    handler.postDelayed({ hideStatus() }, 1500)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrEmpty()) {
                    showStatus(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showStatus("设备不支持语音识别")
            handler.postDelayed({ hideStatus() }, 2000)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            showStatus("启动失败，请重试")
            handler.postDelayed({ hideStatus() }, 2000)
        }
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer.stopListening()
        ballImage.setImageResource(R.drawable.ic_mic)
    }

    private fun rebuildRecognizer() {
        try {
            speechRecognizer.destroy()
        } catch (e: Exception) {}
        setupSpeechRecognizer()
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音悬浮球",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮球运行中"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingBallService::class.java).apply {
            action = "STOP"
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音悬浮球运行中")
            .setContentText("点击悬浮球开始语音输入")
            .setSmallIcon(R.drawable.ic_mic)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.ic_mic, "停止", stopPending)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        try {
            if (::floatingView.isInitialized) windowManager.removeView(floatingView)
            statusView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
