package com.exemplo.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FloatingMacroService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var container: FrameLayout
    private lateinit var triggerFab: FloatingActionButton
    private lateinit var confirmFab: FloatingActionButton
    private val actionFabs = mutableListOf<FloatingActionButton>()

    private var actionCount = 5
    private var actionSize = FloatingActionButton.SIZE_MINI
    private var actionDelay = 500L
    private var actionOrder: IntArray = intArrayOf()

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            actionCount = it.getIntExtra("actionCount", 5)
            actionSize = it.getIntExtra("actionSize", FloatingActionButton.SIZE_MINI)
            actionDelay = it.getLongExtra("actionDelay", 500L)
            actionOrder = it.getIntArrayExtra("actionOrder") ?: IntArray(0)
        }
        setupOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 0
            y = 0
        }

        container = FrameLayout(this)
        windowManager.addView(container, params)

        val density = resources.displayMetrics.density

        // Botão de disparo
        triggerFab = FloatingActionButton(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, (16 * density).toInt(), (80 * density).toInt())
            }
            setImageResource(android.R.drawable.ic_menu_compass)
            setOnClickListener { executeActions() }
        }
        container.addView(triggerFab)

        // Botão confirmar (para esconder ações)
        confirmFab = FloatingActionButton(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, (16 * density).toInt(), (140 * density).toInt())
            }
            setImageResource(android.R.drawable.ic_menu_set_as)
            setOnClickListener {
                hideActionButtons()
            }
        }
        container.addView(confirmFab)

        // Botões de ação
        val icons = intArrayOf(
            android.R.drawable.ic_menu_camera,
            android.R.drawable.ic_menu_gallery,
            android.R.drawable.ic_menu_manage,
            android.R.drawable.ic_menu_send,
            android.R.drawable.ic_menu_share,
            android.R.drawable.ic_menu_call,
            android.R.drawable.ic_menu_directions,
            android.R.drawable.ic_menu_edit,
            android.R.drawable.ic_menu_info_details,
            android.R.drawable.ic_menu_zoom
        )

        for (i in 0 until actionCount) {
            val iconIndex = if (i < actionOrder.size) actionOrder[i] else i
            val iconRes = if (iconIndex in icons.indices) icons[iconIndex] else android.R.drawable.ic_menu_camera
            val fab = FloatingActionButton(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                )
                size = actionSize
                setImageResource(iconRes)
            }
            actionFabs.add(fab)
            container.addView(fab)
        }

        // Posicionar os botões de ação após o layout ser medido
        container.post {
            positionActionFabs()
        }
    }

    private fun positionActionFabs() {
        val density = resources.displayMetrics.density
        val confirmX = confirmFab.x
        val confirmY = confirmFab.y
        val confirmW = confirmFab.width
        val confirmH = confirmFab.height
        val radius = (80 * density).toInt() + confirmW / 2
        val angleStep = Math.PI / (actionCount + 1)

        for (i in actionFabs.indices) {
            val angle = Math.PI - angleStep * (i + 1)
            val dx = (radius * Math.cos(angle)).toInt()
            val dy = -(radius * Math.sin(angle)).toInt()
            actionFabs[i].x = confirmX + confirmW / 2f - actionFabs[i].width / 2f + dx
            actionFabs[i].y = confirmY - actionFabs[i].height + dy
        }
    }

    private fun hideActionButtons() {
        actionFabs.forEach { it.visibility = View.GONE }
        confirmFab.visibility = View.GONE
    }

    private fun executeActions() {
        // Executa as ações uma após a outra com atraso
        for (i in actionFabs.indices) {
            handler.postDelayed({
                Toast.makeText(this@FloatingMacroService, "Ação ${i + 1}", Toast.LENGTH_SHORT).show()
            }, i * actionDelay)
        }
    }

    private fun removeOverlay() {
        if (::windowManager.isInitialized && ::container.isInitialized) {
            windowManager.removeView(container)
        }
    }
}
