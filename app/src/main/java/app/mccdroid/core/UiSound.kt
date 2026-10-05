package app.mccdroid.core

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import app.mccdroid.R

/** Sound effect UI CC0 dari UI SFX soft pack, dipetakan berdasarkan semantik aksi. */
object UiSound {
    private var pool: SoundPool? = null
    private var clickId = 0
    private var successId = 0
    private var errorId = 0
    private var openId = 0
    private var closeId = 0
    private var copyId = 0
    private var sendId = 0
    private var toggleOnId = 0
    private var toggleOffId = 0
    private var deleteId = 0
    private var processingId = 0
    private var notificationId = 0
    private var enabled = true

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build().also {
            clickId = it.load(context, R.raw.sfx_select, 1)
            successId = it.load(context, R.raw.sfx_success, 1)
            errorId = it.load(context, R.raw.sfx_error, 1)
            openId = it.load(context, R.raw.sfx_open, 1)
            closeId = it.load(context, R.raw.sfx_close, 1)
            copyId = it.load(context, R.raw.sfx_copy, 1)
            sendId = it.load(context, R.raw.sfx_send, 1)
            toggleOnId = it.load(context, R.raw.sfx_toggle_on, 1)
            toggleOffId = it.load(context, R.raw.sfx_toggle_off, 1)
            deleteId = it.load(context, R.raw.sfx_delete, 1)
            processingId = it.load(context, R.raw.sfx_processing, 1)
            notificationId = it.load(context, R.raw.sfx_notification, 1)
        }
    }

    fun setEnabled(value: Boolean) { enabled = value }

    fun click() = play(clickId, 0.42f)
    fun success() = play(successId, 0.52f)
    fun alert() = play(errorId, 0.52f)
    fun open() = play(openId, 0.46f)
    fun close() = play(closeId, 0.42f)
    fun copy() = play(copyId, 0.45f)
    fun send() = play(sendId, 0.46f)
    fun toggle(on: Boolean) = play(if (on) toggleOnId else toggleOffId, 0.42f)
    fun delete() = play(deleteId, 0.45f)
    fun processing() = play(processingId, 0.34f)
    fun notification() = play(notificationId, 0.45f)

    private fun play(id: Int, volume: Float) {
        if (enabled && id != 0) pool?.play(id, volume, volume, 1, 0, 1f)
    }
}
