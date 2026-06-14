package com.exemplo.app

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private var isMacroEnabled = false
    private lateinit var mainFab: FloatingActionButton
    private lateinit var triggerFab: FloatingActionButton
    private lateinit var confirmFab: FloatingActionButton
    private val actionFabs = mutableListOf<FloatingActionButton>()
    private var rootLayout: FrameLayout? = null

    // Configurações padrão
    private var actionCount = 5
    private var actionSize = FloatingActionButton.SIZE_MINI
    private var actionDelayMs = 500L
    private var actionOrder: MutableList<Int> = mutableListOf() // índices dos ícones

    // Ícones disponíveis (máximo 10)
    private val availableIcons = intArrayOf(
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        rootLayout = FrameLayout(this)

        // Texto de fundo original
        val textView = TextView(this).apply {
            text = "Olá, Android em Kotlin!"
            textSize = 20f
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout?.addView(textView)

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout!!) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(rootLayout)

        // Carregar configurações salvas
        loadPreferences()

        val density = resources.displayMetrics.density

        // FAB principal (menu flutuante)
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
        rootLayout?.addView(mainFab)

        // Botão de disparo e confirmar serão criados dinamicamente
        triggerFab = createTriggerFab(density)
        triggerFab.visibility = View.GONE
        rootLayout?.addView(triggerFab)

        confirmFab = createConfirmFab(density)
        confirmFab.visibility = View.GONE
        rootLayout?.addView(confirmFab)

        // Se a macro estiver ativa na inicialização, recriar os botões
        if (isMacroEnabled) {
            enableMacro()
        }
    }

    private fun createTriggerFab(density: Float): FloatingActionButton {
        return FloatingActionButton(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, (16 * density).toInt(), (80 * density).toInt())
            }
            setImageResource(android.R.drawable.ic_menu_compass)
            setOnClickListener {
                if (actionFabs.isNotEmpty() && actionFabs.all { it.visibility == View.GONE }) {
                    executeActionsWithDelay(0)
                }
            }
        }
    }

    private fun createConfirmFab(density: Float): FloatingActionButton {
        return FloatingActionButton(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, (16 * density).toInt(), (140 * density).toInt())
            }
            setImageResource(android.R.drawable.ic_menu_set_as)
            setOnClickListener {
                // Esconde todos os botões de ação
                for (fab in actionFabs) {
                    fab.visibility = View.GONE
                }
                confirmFab.visibility = View.GONE
                // triggerFab permanece visível
            }
        }
    }

    private fun showMainMenu() {
        val popup = PopupMenu(this@MainActivity, mainFab)
        // Opção MACRO
        popup.menu.add("MACRO").apply {
            isCheckable = true
            isChecked = isMacroEnabled
        }
        // Opção EDITAR
        popup.menu.add("EDITAR")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "MACRO" -> {
                    item.isChecked = !item.isChecked
                    isMacroEnabled = item.isChecked
                    if (isMacroEnabled) {
                        enableMacro()
                    } else {
                        disableMacro()
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

    private fun enableMacro() {
        // Remove botões antigos, se existirem
        clearActionFabs()

        // Cria novos botões de ação conforme configurações
        val density = resources.displayMetrics.density
        val spacing = (48 * density).toInt() // espaçamento entre botões

        // Ordem definida pelo usuário (índices dos ícones)
        val orderedIcons = actionOrder.map { availableIcons[it] }

        for (i in 0 until actionCount) {
            val iconRes = if (i < orderedIcons.size) orderedIcons[i] else android.R.drawable.ic_menu_camera
            val fab = FloatingActionButton(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                )
                size = actionSize
                setImageResource(iconRes)
                visibility = View.VISIBLE
                // Posicionamento será ajustado abaixo
            }
            actionFabs.add(fab)
            rootLayout?.addView(fab)
        }

        // Aguarda o layout ser medido para posicionar
        confirmFab.post {
            val confirmX = confirmFab.x
            val confirmY = confirmFab.y
            val confirmWidth = confirmFab.width
            val confirmHeight = confirmFab.height

            // Dispor os botões em um arco ao redor do confirmFab
            val radius = (80 * density).toInt() + confirmWidth / 2
            val angleStep = Math.PI / (actionCount + 1) // distribuir no semicírculo superior

            for (i in actionFabs.indices) {
                val angle = Math.PI - angleStep * (i + 1) // de 0 a PI
                val dx = (radius * Math.cos(angle)).toInt()
                val dy = -(radius * Math.sin(angle)).toInt() // negativo para cima

                actionFabs[i].x = confirmX + confirmWidth / 2f - actionFabs[i].width / 2f + dx
                actionFabs[i].y = confirmY - actionFabs[i].height + dy
            }
        }

        triggerFab.visibility = View.VISIBLE
        confirmFab.visibility = View.VISIBLE
    }

    private fun disableMacro() {
        triggerFab.visibility = View.GONE
        confirmFab.visibility = View.GONE
        clearActionFabs()
    }

    private fun clearActionFabs() {
        for (fab in actionFabs) {
            rootLayout?.removeView(fab)
        }
        actionFabs.clear()
    }

    private fun executeActionsWithDelay(index: Int) {
        if (index >= actionFabs.size) return

        // Simula execução da ação (Toast)
        Toast.makeText(this, "Executando ação ${index + 1}", Toast.LENGTH_SHORT).show()

        if (index < actionFabs.size - 1) {
            Handler(Looper.getMainLooper()).postDelayed({
                executeActionsWithDelay(index + 1)
            }, actionDelayMs)
        }
    }

    private fun showEditDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Editar Macro")

        // Layout personalizado
        val inflater = layoutInflater
        val view = inflater.inflate(android.R.layout.select_dialog_item, null) // não é o ideal, vamos criar manualmente

        // Vamos construir um layout simples programaticamente
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        // Campo: quantidade de botões
        val countLabel = TextView(this).apply { text = "Número de botões de ação:" }
        layout.addView(countLabel)
        val countEdit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(actionCount.toString())
        }
        layout.addView(countEdit)

        // Campo: tamanho (spinner)
        val sizeLabel = TextView(this).apply { text = "Tamanho dos botões:" }
        layout.addView(sizeLabel)
        val sizeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                listOf("Mini", "Normal", "Grande")
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(
                when (actionSize) {
                    FloatingActionButton.SIZE_MINI -> 0
                    FloatingActionButton.SIZE_NORMAL -> 1
                    FloatingActionButton.SIZE_AUTO -> 2 // assumindo que "Grande" é SIZE_AUTO (não existe SIZE_LARGE)
                    else -> 1
                }
            )
        }
        layout.addView(sizeSpinner)

        // Campo: atraso (ms)
        val delayLabel = TextView(this).apply { text = "Atraso entre ações (ms):" }
        layout.addView(delayLabel)
        val delayEdit = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(actionDelayMs.toString())
        }
        layout.addView(delayEdit)

        // Campo: ordem (será simplificado: reordenar usando uma lista com botões de subir/descer)
        val orderLabel = TextView(this).apply { text = "Ordem dos ícones (toque para editar):" }
        layout.addView(orderLabel)

        // ListView simples com os nomes dos ícones
        val iconNames = listOf("Câmera", "Galeria", "Gerenciar", "Enviar", "Compartilhar", "Chamada", "Direções", "Editar", "Info", "Zoom")
        val orderAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, iconNames.subList(0, actionCount))
        val orderList = ListView(this).apply {
            adapter = orderAdapter
            // Permitir reordenar? Vamos usar um diálogo à parte, mas manteremos fixo por simplicidade.
        }
        layout.addView(orderList)

        builder.setView(layout)

        builder.setPositiveButton("Salvar") { dialog, _ ->
            val newCount = countEdit.text.toString().toIntOrNull() ?: actionCount
            if (newCount < 1 || newCount > 10) {
                Toast.makeText(this, "Quantidade entre 1 e 10", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            val newSize = when (sizeSpinner.selectedItemPosition) {
                0 -> FloatingActionButton.SIZE_MINI
                1 -> FloatingActionButton.SIZE_NORMAL
                2 -> FloatingActionButton.SIZE_AUTO
                else -> FloatingActionButton.SIZE_MINI
            }
            val newDelay = delayEdit.text.toString().toLongOrNull() ?: actionDelayMs

            // Salvar nova ordem: mantemos a ordem atual dos índices (0..actionCount-1)
            // Se a quantidade mudar, ajustamos a ordem
            val newOrder = if (newCount != actionCount) {
                (0 until newCount).toMutableList()
            } else {
                actionOrder.toMutableList()
            }

            actionCount = newCount
            actionSize = newSize
            actionDelayMs = newDelay
            actionOrder = newOrder

            savePreferences()

            // Se a macro está ativa, recriar os botões
            if (isMacroEnabled) {
                disableMacro()
                enableMacro()
            }

            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("macro_prefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean("macro_enabled", isMacroEnabled)
            putInt("action_count", actionCount)
            putInt("action_size", actionSize)
            putLong("action_delay", actionDelayMs)
            // Salvar a ordem como string de índices separados por vírgula
            putString("action_order", actionOrder.joinToString(","))
            apply()
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("macro_prefs", MODE_PRIVATE)
        isMacroEnabled = prefs.getBoolean("macro_enabled", false)
        actionCount = prefs.getInt("action_count", 5)
        actionSize = prefs.getInt("action_size", FloatingActionButton.SIZE_MINI)
        actionDelayMs = prefs.getLong("action_delay", 500L)
        val orderStr = prefs.getString("action_order", null)
        actionOrder = if (orderStr != null) {
            orderStr.split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
        } else {
            (0 until actionCount).toMutableList()
        }
        // Garantir que a ordem tenha o tamanho correto
        while (actionOrder.size < actionCount) {
            actionOrder.add(actionOrder.size)
        }
    }
}
