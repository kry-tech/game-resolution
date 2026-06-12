package com.exemplo.app

import android.Manifest
import android.app.*
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID
import kotlin.concurrent.thread

data class Perfil(
    val id: String,
    val nome: String,
    val pacote: String,
    val largura: Int,
    val altura: Int,
    val dpi: Int,
    var ativo: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private val perfis = mutableListOf<Perfil>()
    private lateinit var prefs: SharedPreferences
    private lateinit var container: LinearLayout
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        prefs = getSharedPreferences("perfis", MODE_PRIVATE)
        container = findViewById(R.id.container_perfis)
        tvStatus = findViewById(R.id.tv_status)

        findViewById<Button>(R.id.btn_adicionar).setOnClickListener { mostrarDialogPerfil() }
        findViewById<Button>(R.id.btn_conectar).setOnClickListener { mostrarDialogConexao() }
        findViewById<Button>(R.id.btn_restaurar).setOnClickListener { restaurarResolucao() }

        carregarPerfis()
        atualizarUI()
    }

    private fun carregarPerfis() {
        perfis.clear()
        try {
            val arr = JSONArray(prefs.getString("lista", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                perfis.add(Perfil(o.getString("id"), o.getString("nome"), o.getString("pacote"),
                    o.getInt("largura"), o.getInt("altura"), o.getInt("dpi"), o.optBoolean("ativo")))
            }
        } catch (_: Exception) {}
    }

    private fun salvarPerfis() {
        val arr = JSONArray()
        perfis.forEach {
            val o = JSONObject()
            o.put("id", it.id); o.put("nome", it.nome); o.put("pacote", it.pacote)
            o.put("largura", it.largura); o.put("altura", it.altura)
            o.put("dpi", it.dpi); o.put("ativo", it.ativo)
            arr.put(o)
        }
        prefs.edit().putString("lista", arr.toString()).apply()
    }

    private fun atualizarUI() {
        container.removeAllViews()
        perfis.forEachIndexed { i, p ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 12, 16, 12)
                background = getDrawable(android.R.drawable.edit_text)
                (layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 8)
            }
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                (layoutParams as LinearLayout.LayoutParams).weight = 1f
            }
            info.addView(TextView(this).apply {
                text = p.nome; textSize = 16f; setTextColor(0xFFFFFFFF.toInt())
            })
            info.addView(TextView(this).apply {
                text = p.pacote; textSize = 11f; setTextColor(0xFFAAAAAA.toInt())
            })
            info.addView(TextView(this).apply {
                text = "${p.largura}x${p.altura} DPI:${p.dpi}"; textSize = 11f; setTextColor(0xFF888888.toInt())
            })
            card.addView(info)

            val btn = Button(this).apply {
                text = if (p.ativo) "⏹" else "▶"
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(if (p.ativo) 0xFFF44336.toInt() else 0xFF4CAF50.toInt())
                setOnClickListener { togglePerfil(i) }
            }
            card.addView(btn)

            val btnDel = Button(this).apply {
                text = "🗑"; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF666666.toInt())
                setOnClickListener { perfis.removeAt(i); salvarPerfis(); atualizarUI() }
            }
            card.addView(btnDel)
            container.addView(card)
        }
    }

    private fun mostrarDialogPerfil() {
        val etNome = EditText(this).apply { hint = "Nome do jogo" }
        val etPct = EditText(this).apply { hint = "Pacote (ex: com.game.app)" }
        val etW = EditText(this).apply { hint = "Largura (720)"; inputType = 2 }
        val etH = EditText(this).apply { hint = "Altura (1280)"; inputType = 2 }
        val etD = EditText(this).apply { hint = "DPI (280)"; inputType = 2 }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16)
            addView(etNome); addView(etPct)
            addView(LinearLayout(this@MainActivity).apply {
                addView(etW, LinearLayout.LayoutParams(0, -2, 1f))
                addView(etH, LinearLayout.LayoutParams(0, -2, 1f))
            })
            addView(etD)
        }

        AlertDialog.Builder(this).setTitle("Novo Perfil").setView(layout)
            .setPositiveButton("Salvar") { _, _ ->
                if (etNome.text.isBlank() || etPct.text.isBlank()) return@setPositiveButton
                perfis.add(Perfil(UUID.randomUUID().toString(), etNome.text.toString().trim(),
                    etPct.text.toString().trim(),
                    etW.text.toString().toIntOrNull() ?: 720,
                    etH.text.toString().toIntOrNull() ?: 1280,
                    etD.text.toString().toIntOrNull() ?: 280))
                salvarPerfis(); atualizarUI()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun togglePerfil(i: Int) {
        val p = perfis[i]
        if (!temPermissaoShell()) { mostrarDialogConexao(); return }
        if (!temPermissaoUsage()) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); return }

        if (p.ativo) {
            startService(Intent(this, GameService::class.java).apply { action = "PARAR"; putExtra("id", p.id) })
            p.ativo = false
        } else {
            perfis.filter { it.ativo }.forEach {
                startService(Intent(this, GameService::class.java).apply { action = "PARAR"; putExtra("id", it.id) })
                it.ativo = false
            }
            val si = Intent(this, GameService::class.java).apply {
                action = "INICIAR"; putExtra("id", p.id); putExtra("pacote", p.pacote)
                putExtra("largura", p.largura); putExtra("altura", p.altura); putExtra("dpi", p.dpi)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(si) else startService(si)
            p.ativo = true
        }
        salvarPerfis(); atualizarUI()
    }

    // ==================== CONEXÃO POR CÓDIGO DE PAREAMENTO ====================
    private fun mostrarDialogConexao() {
        val etCodigo = EditText(this).apply {
            hint = "Código de 6 dígitos"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            maxLines = 1
            textSize = 22f
            gravity = android.view.Gravity.CENTER
        }
        val tvResultado = TextView(this).apply {
            setTextColor(0xFFFFFF00.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            addView(TextView(this@MainActivity).apply {
                text = """
                    📌 Como parear:
                    
                    1. Vá em Opções do Desenvolvedor
                    2. Ative Depuração Sem Fio
                    3. Toque em "Parear dispositivo com código"
                    4. Digite o código de 6 dígitos abaixo
                    5. Toque em Conectar
                """.trimIndent()
                textSize = 13f
                setTextColor(0xFFCCCCCC.toInt())
            })
            addView(etCodigo)
            addView(tvResultado)
        }

        AlertDialog.Builder(this)
            .setTitle("🔌 Parear Dispositivo")
            .setView(layout)
            .setPositiveButton("Conectar") { _, _ ->
                val codigo = etCodigo.text.toString().trim()
                if (codigo.length != 6 || !codigo.all { it.isDigit() }) {
                    Toast.makeText(this, "Digite um código válido de 6 dígitos!", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                tvResultado.text = "Pareando..."
                thread {
                    val resultado = parearComCodigo(codigo)
                    runOnUiThread {
                        tvResultado.text = resultado
                        if (resultado.contains("✅")) {
                            atualizarStatus()
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    (layout.parent.parent as? AlertDialog)?.dismiss()
                                } catch (_: Exception) {}
                            }, 2000)
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun parearComCodigo(codigo: String): String {
        return try {
            // Método 1: Tenta via shell cmd (Android 11+)
            val resultado1 = executarShell("cmd adb pair $codigo")
            if (resultado1.contains("Successfully") || resultado1.contains("paired")) {
                executarShell("cmd adb connect localhost:5555")
                return "✅ Pareado com sucesso!"
            }

            // Método 2: Tenta via service call (Android 10+)
            val resultado2 = executarShell("service call adb 2 i32 1 s16 $codigo")
            if (resultado2.contains("Result: Parcel(00000000")) {
                executarShell("cmd adb connect localhost:5555")
                return "✅ Pareado com sucesso!"
            }

            // Método 3: Tenta abrir a tela de pareamento do sistema
            runOnUiThread {
                try {
                    val intent = Intent("com.android.settings.ADB_WIRELESS_SETTINGS")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    } catch (e2: Exception) {}
                }
            }

            if (resultado1.isNotEmpty() || resultado2.isNotEmpty()) {
                "⚠️ Resultado: ${resultado1.ifEmpty { resultado2 }}"
            } else {
                "⚠️ Tente parear manualmente nas Opções do Desenvolvedor"
            }
        } catch (e: Exception) {
            "❌ Erro: ${e.message?.take(60)}"
        }
    }

    private fun executarShell(cmd: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = p.inputStream.bufferedReader().readText().trim()
            val err = p.errorStream.bufferedReader().readText().trim()
            p.waitFor()
            out.ifEmpty { err }
        } catch (e: Exception) {
            ""
        }
    }

    private fun restaurarResolucao() {
        try {
            Runtime.getRuntime().exec("wm size reset")
            Runtime.getRuntime().exec("wm density reset")
        } catch (_: Exception) {}
        atualizarStatus()
    }

    private fun atualizarStatus() {
        val ok = temPermissaoShell()
        tvStatus.text = if (ok && temPermissaoUsage()) "🟢 Pronto" else "🔴 Sem permissão"
    }

    private fun temPermissaoShell() = try {
        Runtime.getRuntime().exec("wm size").waitFor() == 0
    } catch (_: Exception) { false }

    private fun temPermissaoUsage() = try {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    override fun onResume() { super.onResume(); atualizarStatus() }
}

// ==================== GAME SERVICE ====================
class GameService : Service() {

    private var pacote = ""
    private var largura = 720
    private var altura = 1280
    private var dpi = 280
    private var ativo = false
    private var jogoAtivo = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { restaurar(); stopSelf(); return START_NOT_STICKY }
        when (intent.getStringExtra("action") ?: intent.action) {
            "INICIAR" -> {
                pacote = intent.getStringExtra("pacote") ?: ""
                largura = intent.getIntExtra("largura", 720)
                altura = intent.getIntExtra("altura", 1280)
                dpi = intent.getIntExtra("dpi", 280)
                iniciar()
            }
            "PARAR" -> { ativo = false; restaurar(); stopForeground(true); stopSelf() }
        }
        return START_STICKY
    }

    private fun iniciar() {
        ativo = true
        mostrarNotificacao()
        Thread {
            while (ativo) {
                try {
                    val app = appFrente()
                    if (app == pacote && !jogoAtivo) {
                        exec("wm size ${largura}x${altura}")
                        exec("wm density $dpi")
                        jogoAtivo = true
                    } else if (app != pacote && jogoAtivo) {
                        restaurar()
                        jogoAtivo = false
                    }
                } catch (_: Exception) {
                    restaurar()
                    jogoAtivo = false
                }
                Thread.sleep(1500)
            }
        }.start()
    }

    private fun appFrente(): String? {
        return try {
            val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, System.currentTimeMillis() - 5000, System.currentTimeMillis())
                .maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Exception) { null }
    }

    private fun exec(cmd: String) {
        try { Runtime.getRuntime().exec(cmd).waitFor() } catch (_: Exception) {}
    }

    private fun restaurar() {
        exec("wm size reset")
        exec("wm density reset")
    }

    private fun mostrarNotificacao() {
        val canal = "game"
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(canal, "Game", NotificationManager.IMPORTANCE_LOW))
        }
        startForeground(1, Notification.Builder(this, canal)
            .setContentTitle("Game Resolution")
            .setContentText("Monitorando: $pacote")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build())
    }

    override fun onBind(i: Intent?) = null
    override fun onDestroy() { ativo = false; restaurar(); super.onDestroy() }
    override fun onTaskRemoved(r: Intent?) { ativo = false; restaurar(); stopSelf() }
}
