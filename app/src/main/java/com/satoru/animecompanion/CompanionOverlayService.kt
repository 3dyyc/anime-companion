package com.satoru.animecompanion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.sin

class CompanionOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var companionView: CompanionView? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DANCE -> {
                showCompanion()
                companionView?.toggleDance(true)
            }
            ACTION_SHOW, null -> {
                showCompanion()
                companionView?.toggleDance(false)
            }
        }
        return START_STICKY
    }

    private fun showCompanion() {
        if (companionView != null) return
        val view = CompanionView(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            dp(176), dp(236), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(180)
        }
        view.onPositionChanged = { dx, dy ->
            params.x += dx
            params.y += dy
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
        view.onTap = {
            if (view.isDancing) view.toggleDance(false) else view.nextMood()
        }
        companionView = view
        try {
            windowManager.addView(view, params)
        } catch (_: Exception) {
            companionView = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        companionView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        companionView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Anime Companion", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Управление плавающей аниме-компаньонкой"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Anime Companion активна")
                .setContentText("Нажми, чтобы открыть управление компаньонкой")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openApp).setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Anime Companion активна")
                .setContentText("Компаньонка работает")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openApp).setOngoing(true).build()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SHOW = "com.satoru.animecompanion.SHOW"
        const val ACTION_DANCE = "com.satoru.animecompanion.DANCE"
        private const val CHANNEL_ID = "anime_companion_channel"
        private const val NOTIFICATION_ID = 7
    }
}

private class CompanionView(context: android.content.Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val characterBitmap by lazy {
        val id = resources.getIdentifier("companion_character", "drawable", context.packageName)
        if (id == 0) null else BitmapFactory.decodeResource(resources, id)
    }
    var isDancing = false
        private set
    var onPositionChanged: ((Int, Int) -> Unit)? = null
    var onTap: (() -> Unit)? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private var mood = 0
    private val startedAt = SystemClock.uptimeMillis()

    fun toggleDance(value: Boolean) { isDancing = value; invalidate() }
    fun nextMood() { mood = (mood + 1) % 4; invalidate() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX; lastTouchY = event.rawY
                downX = event.rawX; downY = event.rawY; dragged = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - lastTouchX).toInt()
                val dy = (event.rawY - lastTouchY).toInt()
                if (kotlin.math.abs(event.rawX - downX) > 8 || kotlin.math.abs(event.rawY - downY) > 8) dragged = true
                if (dragged) onPositionChanged?.invoke(dx, dy)
                lastTouchX = event.rawX; lastTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged) onTap?.invoke()
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 220f, height / 300f)
        canvas.save()
        canvas.translate((width - 220f * scale) / 2f, (height - 300f * scale) / 2f)
        canvas.scale(scale, scale)
        val elapsed = (SystemClock.uptimeMillis() - startedAt) / 1000f
        // Мягкая idle-анимация: дыхание, перенос веса и лёгкое покачивание.
        // В режиме танца движения становятся заметнее, но остаются плавными.
        val bounce = if (isDancing) abs(sin(elapsed * 5f)) * -10f else sin(elapsed * 2.2f) * 2.2f
        canvas.translate(0f, bounce)

        // Use the custom transparent character sprite when available; otherwise retain the fallback.
        val sprite = characterBitmap
        if (sprite != null) {
            val target = RectF(4f, 0f, 216f, 300f)
            val breathing = 1f + sin(elapsed * 2.4f) * 0.012f
            val dancePulse = if (isDancing) 1f + sin(elapsed * 10f) * 0.035f else 1f
            val bodyScaleX = breathing * dancePulse
            val bodyScaleY = (1f - sin(elapsed * 2.4f) * 0.008f) * dancePulse
            val sway = if (isDancing) sin(elapsed * 5f) * 5f else sin(elapsed * 1.45f) * 1.25f
            val tilt = if (isDancing) sin(elapsed * 10f) * 4.5f else sin(elapsed * 1.1f) * 1.1f

            canvas.save()
            canvas.rotate(tilt, 110f, 150f)
            canvas.translate(sway, 0f)
            canvas.scale(bodyScaleX, bodyScaleY, 110f, 150f)
            p.alpha = 255
            canvas.drawBitmap(sprite, null, target, p)

            if (isDancing) {
                // Небольшие искорки подчёркивают танец, не закрывая персонажа.
                p.color = Color.rgb(255, 158, 220)
                p.alpha = 220
                canvas.drawCircle(25f, 88f + sin(elapsed * 5f) * 7f, 4f, p)
                canvas.drawCircle(193f, 115f + sin(elapsed * 6f) * 8f, 5f, p)
                canvas.drawCircle(42f + sin(elapsed * 4f) * 5f, 52f, 2.5f, p)
                canvas.drawCircle(177f, 70f + sin(elapsed * 4.5f) * 6f, 2.5f, p)
            }
            canvas.restore()
            canvas.restore()
            postInvalidateDelayed(33)
            return
        }

        p.color = 0x26000000
        canvas.drawOval(RectF(40f, 276f, 184f, 296f), p)
        p.color = Color.rgb(244, 203, 204)
        canvas.drawRoundRect(RectF(68f, 221f, 101f, 276f), 15f, 15f, p)
        canvas.drawRoundRect(RectF(120f, 221f, 153f, 276f), 15f, 15f, p)
        p.color = Color.rgb(48, 47, 59)
        canvas.drawRoundRect(RectF(67f, 239f, 102f, 284f), 13f, 13f, p)
        canvas.drawRoundRect(RectF(119f, 239f, 154f, 284f), 13f, 13f, p)

        p.color = Color.rgb(48, 48, 61)
        canvas.drawRoundRect(RectF(48f, 133f, 174f, 238f), 32f, 32f, p)
        canvas.drawRoundRect(RectF(20f, 140f, 63f, 205f), 20f, 20f, p)
        canvas.drawRoundRect(RectF(159f, 140f, 201f, 205f), 20f, 20f, p)
        p.color = Color.rgb(36, 36, 48)
        canvas.drawRoundRect(RectF(28f, 157f, 56f, 190f), 12f, 12f, p)
        canvas.drawRoundRect(RectF(166f, 157f, 194f, 190f), 12f, 12f, p)
        p.color = Color.rgb(225, 215, 226)
        p.strokeWidth = 2f
        canvas.drawLine(92f, 156f, 92f, 191f, p)
        canvas.drawLine(130f, 156f, 130f, 191f, p)

        p.color = Color.rgb(255, 224, 211)
        canvas.drawRoundRect(RectF(98f, 107f, 126f, 145f), 10f, 10f, p)
        canvas.drawOval(RectF(48f, 13f, 173f, 150f), setColor(p, Color.rgb(75, 54, 65)))
        canvas.drawOval(RectF(61f, 34f, 159f, 137f), setColor(p, Color.rgb(255, 224, 211)))
        p.color = Color.rgb(75, 54, 65)
        canvas.drawRoundRect(RectF(52f, 35f, 72f, 137f), 10f, 10f, p)
        canvas.drawRoundRect(RectF(146f, 34f, 166f, 142f), 10f, 10f, p)
        val fringe = Path().apply {
            moveTo(55f, 53f); quadTo(80f, 3f, 116f, 28f); quadTo(145f, 12f, 160f, 56f)
            lineTo(147f, 75f); lineTo(135f, 61f); lineTo(122f, 78f); lineTo(111f, 59f)
            lineTo(96f, 77f); lineTo(82f, 60f); lineTo(68f, 76f); close()
        }
        canvas.drawPath(fringe, p)

        p.color = 0x55F08091
        canvas.drawOval(RectF(69f, 94f, 91f, 104f), p)
        canvas.drawOval(RectF(132f, 94f, 153f, 104f), p)
        val blinking = elapsed % 3.7f > 3.48f && elapsed % 3.7f < 3.62f
        if (blinking) {
            p.color = Color.rgb(92, 59, 77); p.strokeWidth = 3f
            canvas.drawLine(78f, 88f, 95f, 88f, p); canvas.drawLine(128f, 88f, 145f, 88f, p)
        } else {
            p.color = Color.WHITE
            canvas.drawOval(RectF(76f, 75f, 98f, 98f), p); canvas.drawOval(RectF(126f, 75f, 148f, 98f), p)
            p.color = Color.rgb(112, 76, 96)
            canvas.drawOval(RectF(80f, 78f, 95f, 97f), p); canvas.drawOval(RectF(130f, 78f, 145f, 97f), p)
            p.color = Color.WHITE
            canvas.drawCircle(85f, 82f, 4f, p); canvas.drawCircle(135f, 82f, 4f, p)
        }

        p.color = Color.rgb(133, 75, 91); p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f
        val mouth = Path()
        when (mood) {
            1 -> { mouth.moveTo(101f, 111f); mouth.quadTo(110f, 120f, 120f, 111f) }
            2 -> { mouth.moveTo(101f, 116f); mouth.quadTo(110f, 108f, 120f, 116f) }
            3 -> { canvas.drawOval(RectF(105f, 108f, 116f, 121f), p) }
            else -> { mouth.moveTo(101f, 110f); mouth.quadTo(111f, 119f, 121f, 109f) }
        }
        canvas.drawPath(mouth, p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(255, 168, 221)
        canvas.drawRoundRect(RectF(54f, 42f, 67f, 48f), 3f, 3f, p)
        canvas.drawRoundRect(RectF(151f, 46f, 164f, 52f), 3f, 3f, p)
        if (isDancing) {
            p.color = Color.rgb(255, 158, 220)
            canvas.drawCircle(28f, 90f + sin(elapsed * 5f) * 7f, 4f, p)
            canvas.drawCircle(190f, 115f + sin(elapsed * 6f) * 8f, 5f, p)
        }
        canvas.restore()
        postInvalidateDelayed(45)
    }

    private fun setColor(paint: Paint, color: Int): Paint { paint.color = color; return paint }
}
