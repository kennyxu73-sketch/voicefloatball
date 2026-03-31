package com.voiceball.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

/**
 * 透明语音识别Activity
 * 展示录音动画，识别完成后通过无障碍服务填入文字
 */
class VoiceRecognitionActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tvStatus: TextView
    private lateinit var ivWave: ImageView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 透明窗口，不遮挡内容
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)

        setContentView(R.layout.activity_voice_recognition)

        tvStatus = findViewById(R.id.tv_status)
        ivWave = findViewById(R.id.iv_wave)

        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "设备不支持语音识别"
            handler.postDelayed({ finish() }, 2000)
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                tvStatus.text = "请说话..."
                ivWave.setImageResource(R.drawable.ic_wave_active)
            }

            override fun onBeginningOfSpeech() {
                tvStatus.text = "识别中..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 可根据音量动态更新波形图
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                tvStatus.text = "处理中..."
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "录音错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NO_MATCH -> "未识别到内容"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器繁忙"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "等待超时"
                    else -> "识别失败($error)"
                }
                tvStatus.text = msg
                handler.postDelayed({ finish() }, 1500)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return

                tvStatus.text = "\"$text\""

                // 通过无障碍服务填入文字
                VoiceAccessibilityService.instance?.insertText(text)

                handler.postDelayed({ finish() }, 800)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrEmpty()) {
                    tvStatus.text = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        speechRecognizer.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        // 重置悬浮球图标
        // FloatingBallService instance would reset here if needed
    }

    override fun onBackPressed() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.stopListening()
        }
        super.onBackPressed()
    }
}
