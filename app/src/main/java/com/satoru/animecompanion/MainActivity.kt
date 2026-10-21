package com.satoru.animecompanion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        requestNotificationPermissionIfNeeded()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            setBackgroundColor(Color.rgb(255, 247, 252))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(TextView(this).apply {
            text = "🌸 Anime Companion"
            textSize = 29f
            setTextColor(Color.rgb(90, 48, 82))
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "Твоя маленькая аниме-компаньонка"
            textSize = 16f
            setTextColor(Color.rgb(126, 91, 119))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        }, matchWrap())
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(70, 70, 70))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.WHITE)
        }
        content.addView(status, matchWrap())
        content.addView(makeButton("Разрешить отображение поверх приложений") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else refreshStatus()
        }, buttonParams())
        content.addView(makeButton("💗 Позвать компаньонку") {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, CompanionOverlayService::class.java).setAction(CompanionOverlayService.ACTION_SHOW)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                status.text = "Компаньонка гуляет по экрану! Перетащи её пальцем или коснись, чтобы сменить настроение."
            } else {
                status.text = "Сначала разреши отображение поверх других приложений."
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }, buttonParams())
        content.addView(makeButton("💃 Танцевать!") {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, CompanionOverlayService::class.java).setAction(CompanionOverlayService.ACTION_DANCE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                status.text = "Танцевальный режим включён! Коснись компаньонки, чтобы остановить танец."
            } else status.text = "Сначала разреши отображение поверх других приложений."
        }, buttonParams())
        content.addView(makeButton("🌙 Спрятать компаньонку") {
            stopService(Intent(this, CompanionOverlayService::class.java))
            status.text = "Компаньонка спряталась. Её можно позвать снова в любой момент."
        }, buttonParams())
        content.addView(TextView(this).apply {
            text = "Первая сборка — технический прототип: персонаж нарисован кодом. Позже заменим его на подготовленные анимации твоей героини, добавим коллекцию персонажей, характер и несколько танцев."
            textSize = 14f
            setTextColor(Color.rgb(112, 93, 108))
            setPadding(dp(4), dp(22), dp(4), dp(8))
        }, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.rgb(83, 39, 75))
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(250, 218, 242)))
            setOnClickListener { action() }
        }

    private fun refreshStatus() {
        status.text = if (Settings.canDrawOverlays(this)) {
            "Разрешение готово 🌸 Можно запускать компаньонку."
        } else {
            "Разреши отображение поверх других приложений, чтобы девочка могла жить вне этого приложения."
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun buttonParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
}
