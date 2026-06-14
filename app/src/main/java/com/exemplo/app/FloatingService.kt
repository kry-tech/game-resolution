package com.exemplo.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.*

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: LinearLayout? = null

    private var macroEnabled = false
    private val clickButtons = mutableListOf<Button>()
    private var shootButton: Button? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)

            val toggleMacro = Switch(this@FloatingService).apply {
                text = "MACRO"
                setOnCheckedChangeListener { _, isChecked ->
                    macroEnabled = isChecked
                    updateMacroUI()
                }
            }

            addView(toggleMacro)

            shootButton = Button(context).apply {
                text = "DISPARO"
                visibility = Button.GONE
            }
            addView(shootButton)

            repeat(3) { index ->
                val btn = Button(context).apply {
                    text = "CLICK ${index + 1}"
                    visibility = Button.GONE
                }
                clickButtons.add(btn)
                addView(btn)
            }

            val fechar = Button(context).apply {
                text = "Fechar"
                setOnClickListener { stopSelf() }
            }

            addView(fechar)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 200

        floatingView!!.setOnTouchListener(object : android.view.View.OnTouchListener {

            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: android.view.View, event: MotionEvent): Boolean {

                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()

                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
    }

    private fun updateMacroUI() {
        if (macroEnabled) {
            shootButton?.visibility = Button.VISIBLE
            clickButtons.forEach { it.visibility = Button.VISIBLE }
        } else {
            shootButton?.visibility = Button.GONE
            clickButtons.forEach { it.visibility = Button.GONE }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
        }
    }
}
