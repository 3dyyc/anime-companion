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
import kotlin.math.abs
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

        // Optional cut-out rig: each layer is a transparent PNG on the same 220x300 canvas.
        // If the full set is present, animate hair, arms and skirt around separate pivots.
        // Until then, keep using the original sprite below so no assets disappear.
        val rigBackHair = layerBitmap("companion_back_hair")
        val rigLegs = layerBitmap("companion_legs")
        val rigBody = layerBitmap("companion_body")
        val rigArms = layerBitmap("companion_arms")
        val rigSkirt = layerBitmap("companion_skirt")
        val rigFace = layerBitmap("companion_face")
        val rigFrontHair = layerBitmap("companion_front_hair")
        if (rigBackHair != null && rigLegs != null && rigBody != null && rigArms != null &&
            rigSkirt != null && rigFace != null && rigFrontHair != null) {
            val target = RectF(0f, 0f, 220f, 300f)
            fun layer(bitmap: android.graphics.Bitmap, pivotX: Float = 110f, pivotY: Float = 150f,
                      angle: Float = 0f, sx: Float = 1f, sy: Float = 1f) {
                canvas.save()
                canvas.rotate(angle, pivotX, pivotY)
                canvas.scale(sx, sy, pivotX, pivotY)
                canvas.drawBitmap(bitmap, null, target, p)
                canvas.restore()
            }
            p.alpha = 255
            layer(rigBackHair, 110f, 55f, sin(elapsed * if (isDancing) 8f else 2.2f) * if (isDancing) 5f else 1.2f)
            layer(rigLegs)
            val breathe = sin(elapsed * 2.4f)
            layer(rigBody, 110f, 145f, sin(elapsed * if (isDancing) 8f else 1.4f) * if (isDancing) 3f else 0.5f,
                1f + breathe * 0.008f, 1f - breathe * 0.006f)
            layer(rigSkirt, 110f, 178f, sin(elapsed * if (isDancing) 10f else 2.8f) * if (isDancing) 4.5f else 1.1f,
                1f + abs(sin(elapsed * if (isDancing) 10f else 2.8f)) * if (isDancing) 0.018f else 0.004f, 1f)
            layer(rigArms, 110f, 133f, sin(elapsed * if (isDancing) 9f else 1.8f) * if (isDancing) 9f else 1.5f)
            layer(rigFace, 110f, 86f, sin(elapsed * 1.5f) * 0.5f)
            layer(rigFrontHair, 110f, 52f, sin(elapsed * if (isDancing) 8f else 2.4f) * if (isDancing) 2.8f else 0.7f)
            if (isDancing) {
                p.color = Color.rgb(255, 158, 220); p.alpha = 220
                canvas.drawCircle(25f, 88f + sin(elapsed * 5f) * 7f, 4f, p)
                canvas.drawCircle(193f, 115f + sin(elapsed * 6f) * 8f, 5f, p)
            }
            p.alpha = 255
            canvas.restore()
            postInvalidateDelayed(33)
            return
        }

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

            // Мягкая деформация сетки позволяет оживить отдельные зоны даже у одного PNG:
            // кончики волос слегка колышутся, а край юбки реагирует на шаги и танец.
            // Это не требует вырезать части персонажа и не создаёт двойных контуров.
            val meshCols = 8
            val meshRows = 12
            val mesh = FloatArray((meshCols + 1) * (meshRows + 1) * 2)
            val hairPhase = sin(elapsed * if (isDancing) 8.5f else 2.1f)
            val skirtPhase = sin(elapsed * if (isDancing) 10.5f else 2.7f)
            val hairAmplitude = if (isDancing) 3.2f else 0.85f
            val skirtAmplitude = if (isDancing) 4.2f else 1.1f
            for (row in 0..meshRows) {
                val v = row.toFloat() / meshRows
                for (col in 0..meshCols) {
                    val u = col.toFloat() / meshCols
                    val edge = abs(u * 2f - 1f)
                    val hairBand = (1f - abs(v - 0.16f) / 0.19f).coerceIn(0f, 1f)
                    val skirtBand = (1f - abs(v - 0.64f) / 0.16f).coerceIn(0f, 1f)
                    val hairDx = hairPhase * hairAmplitude * hairBand * edge
                    val skirtDx = skirtPhase * skirtAmplitude * skirtBand * edge
                    val skirtDy = abs(skirtPhase) * skirtAmplitude * 0.16f * skirtBand * edge
                    val index = (row * (meshCols + 1) + col) * 2
                    mesh[index] = target.left + u * target.width() + hairDx + skirtDx
                    mesh[index + 1] = target.top + v * target.height() + skirtDy
                }
            }
            canvas.drawBitmapMesh(sprite, meshCols, meshRows, mesh, 0, null, 0, p)

            // Пока исходник остаётся единым PNG, mesh-анимация даёт локальное движение
            // волос и ткани; независимые суставы включатся автоматически, когда появятся слои.

            // Лёгкие линии движения возле волос и края юбки создают ощущение инерции,
            // не закрывая рисунок и не копируя его поверх самого себя.
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.4f
            p.color = Color.rgb(255, 220, 240)
            p.alpha = 115
            val hairMotion = sin(elapsed * if (isDancing) 8f else 2.3f) * if (isDancing) 4f else 1.2f
            canvas.drawLine(34f, 34f + hairMotion, 43f, 29f + hairMotion, p)
            canvas.drawLine(185f, 39f - hairMotion, 194f, 34f - hairMotion, p)
            val fabricMotion = sin(elapsed * if (isDancing) 10f else 3f) * if (isDancing) 3f else 0.8f
            canvas.drawLine(61f, 169f + fabricMotion, 70f, 172f + fabricMotion, p)
            canvas.drawLine(150f, 172f - fabricMotion, 159f, 169f - fabricMotion, p)
            p.style = Paint.Style.FILL
            p.alpha = 255

            // Реакции лица поверх исходного спрайта: смущение, игривость и удивление.
            // Координаты рассчитаны относительно области 220x300 с персонажем в полный рост.
            when (mood) {
                1 -> {
                    p.color = Color.rgb(255, 105, 160)
                    p.alpha = (115 + abs(sin(elapsed * 2.8f)) * 45).toInt()
                    canvas.drawOval(RectF(86f, 47f, 101f, 53f), p)
                    canvas.drawOval(RectF(130f, 47f, 145f, 53f), p)
                }
                2 -> {
                    // Игривые сердечки у головы — кокетливая реакция на повторное касание.
                    p.color = Color.rgb(255, 92, 174)
                    p.alpha = 235
                    val bob = sin(elapsed * 4f) * 3f
                    drawHeart(canvas, 78f, 30f + bob, 5f, p)
                    drawHeart(canvas, 148f, 35f - bob, 4f, p)
                }
                3 -> {
                    p.color = Color.rgb(115, 239, 255)
                    p.alpha = 230
                    canvas.drawCircle(75f, 36f + sin(elapsed * 5f) * 2f, 2.5f, p)
                    canvas.drawCircle(151f, 43f + sin(elapsed * 4f) * 2f, 3f, p)
                }
            }

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

    private fun layerBitmap(name: String): android.graphics.Bitmap? {
        val id = resources.getIdentifier(name, "drawable", context.packageName)
        return if (id == 0) null else BitmapFactory.decodeResource(resources, id)
    }

    private fun drawHeart(canvas: Canvas, x: Float, y: Float, size: Float, paint: Paint) {
        val heart = Path().apply {
            moveTo(x, y + size)
            cubicTo(x - size * 1.7f, y, x - size, y - size, x, y - size * 0.25f)
            cubicTo(x + size, y - size, x + size * 1.7f, y, x, y + size)
            close()
        }
        canvas.drawPath(heart, paint)
    }

    private fun setColor(paint: Paint, color: Int): Paint { paint.color = color; return paint }
}
