package com.exemplo.app

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : ComponentActivity() {

    private lateinit var mainFab: FloatingActionButton
    private var isMacroEnabled = false

    // Configurações
    private var actionCount = 5
    private var actionSize = FloatingActionButton.SIZE_MINI
    private var actionDelayMs = 500L
    private var actionOrder: MutableList<Int> = mutableListOf()

    companion object {
        const val PREFS_NAME = "macro_prefs"
        const val KEY_ENABLED = "macro_enabled"
        const val KEY_COUNT = "action_count"
        const val KEY_SIZE = "action_size"
        const val KEY_DELAY = "action_delay"
        const val KEY_ORDER = "action_order"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rootLayout = FrameLayout(this)

        val textView = TextView(this).apply {
            text = "Olá, Android em Kotlin!"
            textSize = 20f
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(textView)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(rootLayout)

        loadPreferences()

        val density = resources.displayMetrics.density

        mainFab = FloatingActionButton(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, (16 * density).toInt(), (16 * density).toInt())
            }
            setImageResource(android.R.drawable.ic_menu_add)
            setOnClickListener { showMainMenu() }
        }
        rootLayout.addView(mainFab)
    }

    private fun showMainMenu() {
        val popup = PopupMenu(this, mainFab)
        popup.menu.add("MACRO").apply {
            isCheckable = true
            isChecked = isMacroEnabled
        }
        popup.menu.add("EDITAR")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "MACRO" -> {
                    item.isChecked = !item.isChecked
                    isMacroEnabled = item.isChecked
                    if (isMacroEnabled) {
                        startFloatingService()
                    } else {
                        stopFloatingService()
                    }
                    savePreferences()
                    true
                }
                "EDITAR" -> {
                    showEditDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun startFloatingService() {
        // Verifica se a permissão de sobreposição foi concedida
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Permita sobreposição nas configurações", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivity(intent)
                // Não inicia o serviço agora, o usuário precisa permitir
                // Após conceder, ele pode ativar novamente
                isMacroEnabled = false // volta ao estado anterior
                return
            }
        }

        val serviceIntent = Intent(this, FloatingMacroService::class.java)
        serviceIntent.putExtra("actionCount", actionCount)
        serviceIntent.putExtra("actionSize", actionSize)
        serviceIntent.putExtra("actionDelay", actionDelayMs)
        serviceIntent.putExtra("actionOrder", actionOrder.toIntArray())
        startService(serviceIntent)
    }

    private fun stopFloatingService() {
        stopService(Intent(this, FloatingMacroService::class.java))
    }

    private fun showEditDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Editar Macro")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 30, 60, 30)
        }

        // Quantidade
        layout.addView(TextView(this).apply { text = "Número de botões (1-10):" })
        val countEdit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(actionCount.toString())
        }
        layout.addView(countEdit)

        // Tamanho
        layout.addView(TextView(this).apply { text = "Tamanho:" })
        val sizeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter<String>(this@MainActivity,
                android.R.layout.simple_spinner_item,
                listOf("Mini", "Normal", "Grande")
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(when (actionSize) {
                FloatingActionButton.SIZE_MINI -> 0
                FloatingActionButton.SIZE_NORMAL -> 1
                else -> 2
            })
        }
        layout.addView(sizeSpinner)

        // Atraso
        layout.addView(TextView(this).apply { text = "Atraso entre ações (ms):" })
        val delayEdit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(actionDelayMs.toString())
        }
        layout.addView(delayEdit)

        builder.setView(layout)

        builder.setPositiveButton("Salvar") { dialog, _ ->
            val newCount = countEdit.text.toString().toIntOrNull() ?: actionCount
            if (newCount !in 1..10) {
                Toast.makeText(this, "Valor inválido (1-10)", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            actionCount = newCount
            actionSize = when (sizeSpinner.selectedItemPosition) {
                0 -> FloatingActionButton.SIZE_MINI
                1 -> FloatingActionButton.SIZE_NORMAL
                2 -> FloatingActionButton.SIZE_AUTO
                else -> FloatingActionButton.SIZE_MINI
            }
            actionDelayMs = delayEdit.text.toString().toLongOrNull() ?: 500L

            // Ajustar a ordem caso a quantidade tenha mudado
            while (actionOrder.size < actionCount) {
                actionOrder.add(actionOrder.size)
            }
            if (actionOrder.size > actionCount) {
                actionOrder = actionOrder.subList(0, actionCount).toMutableList()
            }

            savePreferences()

            // Se o serviço está ativo, recria com as novas configs
            if (isMacroEnabled) {
                stopFloatingService()
                startFloatingService()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun savePreferences() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putBoolean(KEY_ENABLED, isMacroEnabled)
            putInt(KEY_COUNT, actionCount)
            putInt(KEY_SIZE, actionSize)
            putLong(KEY_DELAY, actionDelayMs)
            putString(KEY_ORDER, actionOrder.joinToString(","))
            apply()
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        isMacroEnabled = prefs.getBoolean(KEY_ENABLED, false)
        actionCount = prefs.getInt(KEY_COUNT, 5)
        actionSize = prefs.getInt(KEY_SIZE, FloatingActionButton.SIZE_MINI)
        actionDelayMs = prefs.getLong(KEY_DELAY, 500L)
        val orderStr = prefs.getString(KEY_ORDER, null)
        actionOrder = if (orderStr != null) {
            orderStr.split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
        } else {
            (0 until actionCount).toMutableList()
        }
        while (actionOrder.size < actionCount) {
            actionOrder.add(actionOrder.size)
        }
    }
}
