package com.succh.unifeed.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/** 朗读播放状态：空闲 / 朗读中 / 已暂停 */
enum class TtsPlaybackState { Idle, Playing, Paused }

/**
 * 阅读页 TTS 封装：分段顺序朗读、暂停/继续、语速、无引擎或无中文语音时明确报错。
 * 回调经主线程写 Compose 状态，避免引擎线程直接改 UI。
 */
class ReaderTtsController(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val _state = mutableStateOf(TtsPlaybackState.Idle)
    val state: State<TtsPlaybackState> = _state

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private var tts: TextToSpeech? = null
    private var ready = false
    private var initializing = false
    private var chunks: List<String> = emptyList()
    private var index = 0
    private var rate = 1.0f
    private var paused = false
    private var session = 0
    private var expectedUtterance: String? = null

    fun consumeError() {
        _error.value = null
    }

    /** 开始朗读；已在播则先停再重来 */
    fun start(chunks: List<String>, rate: Float) {
        stopInternal(notifyIdle = false)
        this.chunks = chunks
        this.index = 0
        this.rate = rate.coerceIn(0.5f, 2.0f)
        this.paused = false
        _state.value = TtsPlaybackState.Playing
        if (ready) {
            speakCurrent()
        } else {
            ensureInit()
        }
    }

    fun pause() {
        if (_state.value != TtsPlaybackState.Playing) return
        paused = true
        expectedUtterance = null
        tts?.stop()
        _state.value = TtsPlaybackState.Paused
    }

    fun resume() {
        if (_state.value != TtsPlaybackState.Paused) return
        paused = false
        _state.value = TtsPlaybackState.Playing
        if (ready) speakCurrent() else ensureInit()
    }

    fun stop() {
        stopInternal(notifyIdle = true)
    }

    /** 切换语速后对后续 utterance 生效 */
    fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(this.rate)
    }

    fun release() {
        stopInternal(notifyIdle = true)
        ready = false
        initializing = false
        tts?.shutdown()
        tts = null
    }

    private fun stopInternal(notifyIdle: Boolean) {
        session++
        paused = false
        expectedUtterance = null
        chunks = emptyList()
        index = 0
        tts?.stop()
        if (notifyIdle) _state.value = TtsPlaybackState.Idle
    }

    private fun ensureInit() {
        if (ready || initializing) return
        initializing = true
        val engine = TextToSpeech(appContext) { status ->
            main.post { onInit(status) }
        }
        tts = engine
    }

    private fun onInit(status: Int) {
        initializing = false
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            failAndReset("设备未安装语音引擎，无法朗读")
            return
        }
        if (engine.engines.isNullOrEmpty()) {
            failAndReset("设备未安装语音引擎，无法朗读")
            return
        }
        if (!applyChineseVoice(engine)) {
            failAndReset("未安装中文语音数据，无法朗读")
            return
        }
        ready = true
        engine.setOnUtteranceProgressListener(utteranceListener)
        engine.setSpeechRate(rate)
        if (_state.value == TtsPlaybackState.Playing && !paused && chunks.isNotEmpty()) {
            speakCurrent()
        }
    }

    private fun applyChineseVoice(engine: TextToSpeech): Boolean {
        val locales = listOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINESE, Locale.TAIWAN)
        for (locale in locales) {
            val available = engine.isLanguageAvailable(locale)
            if (available == TextToSpeech.LANG_MISSING_DATA ||
                available == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                continue
            }
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                return true
            }
        }
        return false
    }

    private fun speakCurrent() {
        val engine = tts
        if (engine == null || !ready) return
        if (index >= chunks.size) {
            finishAll()
            return
        }
        engine.setSpeechRate(rate)
        val id = "${session}:$index"
        expectedUtterance = id
        val code = engine.speak(chunks[index], TextToSpeech.QUEUE_FLUSH, null, id)
        if (code == TextToSpeech.ERROR) {
            failAndReset("朗读失败，请稍后重试")
        }
    }

    private fun finishAll() {
        session++
        paused = false
        expectedUtterance = null
        chunks = emptyList()
        index = 0
        _state.value = TtsPlaybackState.Idle
    }

    private fun failAndReset(message: String) {
        stopInternal(notifyIdle = true)
        ready = false
        initializing = false
        tts?.shutdown()
        tts = null
        _error.value = message
    }

    private fun onUtteranceDone(utteranceId: String?) {
        if (utteranceId != expectedUtterance) return
        if (paused || _state.value != TtsPlaybackState.Playing) return
        index++
        speakCurrent()
    }

    private fun onUtteranceError(utteranceId: String?) {
        if (utteranceId != expectedUtterance) return
        if (paused || _state.value != TtsPlaybackState.Playing) return
        failAndReset("朗读失败，请稍后重试")
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            main.post { onUtteranceDone(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { onUtteranceError(utteranceId) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { onUtteranceError(utteranceId) }
        }
    }
}
