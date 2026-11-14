package com.example.p4

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.math.exp
import kotlin.math.roundToInt

// ---- 調整用の定数 ----------------------------------------------------------

/** 輝度の段数。増やすと1ステップが細かくなる。 */
private const val LEVELS = 16

// Android標準の輝度スライダーと同じHLGカーブの係数。
// 見た目の等間隔さが合わない場合はここを調整する。
private const val HLG_R = 0.5
private const val HLG_A = 0.17883277
private const val HLG_B = 0.28466892
private const val HLG_C = 0.55991073

/**
 * 見た目の位置 g（0.0〜1.0）を、端末の輝度設定値（min〜max）に変換する。
 * g を等間隔に刻むと、見た目にほぼ等間隔な輝度になる。
 */
internal fun levelToBrightness(g: Double, min: Int, max: Int): Int {
    val lin = if (g <= HLG_R) {
        val x = g / HLG_R
        x * x
    } else {
        exp((g - HLG_C) / HLG_A) + HLG_B
    }
    val ratio = (lin / 12.0).coerceIn(0.0, 1.0)
    return (min + (max - min) * ratio).roundToInt()
}

/** 輝度ステップ表を作る。重複を除いた昇順の設定値リスト。 */
internal fun buildBrightnessTable(min: Int, max: Int): List<Int> =
    (0 until LEVELS)
        .map { i -> levelToBrightness(i.toDouble() / (LEVELS - 1), min, max) }
        .distinct()
        .sorted()

// ---- サービス本体 ----------------------------------------------------------

/**
 * 画面オン中の音量キーをすべて消費し、
 *   短押し → 輝度を1ステップ
 *   長押し → 音量を1段階だけ（押し続けても繰り返さない）
 * に置き換える。画面オフ中はキーイベントが届かないので何もしない。
 */
class KeyService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var powerManager: PowerManager
    private var table: List<Int> = emptyList()

    /** 押下中の音量キー。なければ null。 */
    private var pendingKeyCode: Int? = null

    /** 今回の押下で長押しタイマーが発火済みか。 */
    private var longPressFired = false

    private val longPressRunnable = Runnable {
        val keyCode = pendingKeyCode ?: return@Runnable
        longPressFired = true
        // 押下中に画面が消えた場合は何もしない
        if (!powerManager.isInteractive) return@Runnable
        // 1回だけ実行する。タイマーは再設定しない（音量が上がり続けるのを防ぐ）
        adjustVolumeOnce(up = keyCode == KeyEvent.KEYCODE_VOLUME_UP)
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        val min = systemInt("config_screenBrightnessSettingMinimum", 1).coerceAtLeast(1)
        val max = systemInt("config_screenBrightnessSettingMaximum", 255).coerceAtLeast(min + 1)
        table = buildBrightnessTable(min, max)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        // ここから先は必ず true を返す（音量キーは DOWN も UP もすべて消費する）
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    // 新しい押下は、前の押下を破棄して上書きする
                    resetState()
                    pendingKeyCode = keyCode
                    handler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }
            }

            KeyEvent.ACTION_UP -> {
                if (keyCode == pendingKeyCode) {
                    val wasLongPress = longPressFired
                    resetState()
                    if (!wasLongPress) {
                        stepBrightness(up = keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                    }
                }
            }
        }
        return true
    }

    // ---- 音量 --------------------------------------------------------------

    private fun adjustVolumeOnce(up: Boolean) {
        val stream = when (audioManager.mode) {
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        try {
            audioManager.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: SecurityException) {
            // おやすみモード関連などで拒否された場合は何もしない
        }
    }

    // ---- 輝度 --------------------------------------------------------------

    private fun stepBrightness(up: Boolean) {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "システム設定の変更を許可してください", Toast.LENGTH_SHORT).show()
            return
        }

        val resolver = contentResolver
        val current = try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            return
        }

        // 現在値より上（下）で最も近い表の値へ移動する。
        // 「最寄りの番号±1」方式は暗い側で値が動かなくなるので使わない。
        val next = if (up) {
            table.firstOrNull { it > current }
        } else {
            table.lastOrNull { it < current }
        } ?: return

        try {
            val mode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, next)
        } catch (e: SecurityException) {
            // 権限が直前に取り消された場合など
        }
    }

    /** システムの整数リソースを名前で引く。取れなければ fallback。 */
    private fun systemInt(name: String, fallback: Int): Int {
        val res = Resources.getSystem()
        val id = res.getIdentifier(name, "integer", "android")
        if (id == 0) return fallback
        return try {
            res.getInteger(id)
        } catch (e: Resources.NotFoundException) {
            fallback
        }
    }

    // ---- 状態管理とライフサイクル ------------------------------------------

    private fun resetState() {
        handler.removeCallbacks(longPressRunnable)
        pendingKeyCode = null
        longPressFired = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // アクセシビリティイベントは使わない
    }

    override fun onInterrupt() {
        resetState()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        resetState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        resetState()
        super.onDestroy()
    }
}
