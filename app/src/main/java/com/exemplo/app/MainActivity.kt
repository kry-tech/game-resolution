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

// ==================== DATA CLASS ====================
data class Perfil(
    val id: String,
    val nome: String,
    val pacote: String,
    val largura: Int,
    val altura: Int,
    val dpi: Int,
    var ativo: Boolean = false
)

// ==================== MAIN ACTIVITY ====================
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

    // ==================== PERFIS ====================
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

    // ==================== CONEXÃO VIA IP ====================
    private fun mostrarDialogConexao() {
        val tvInstrucoes = TextView(this).apply {
            text = """
                📌 Passo a passo:
                
                1. Ative as Opções do Desenvolvedor
                2. Ative a Depuração USB
                3. Ative a Depuração Sem Fio (Wi-Fi)
                4. Anote o IP e porta que aparecer
                5. Digite abaixo:
            """.trimIndent()
            textSize = 13f
            setTextColor(0xFFCCCCCC.toInt())
        }

        val etIP = EditText(this).apply {
            hint = "IP:Porta (ex: 192.168.1.10:5555)"
            setText(prefs.getString("ip", "192.168.1."))
        }
        val tvResultado = TextView(this).apply { setTextColor(0xFFFFFF00.toInt()); textSize = 13f }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16)
            addView(tvInstrucoes); addView(etIP); addView(tvResultado)
        }

        AlertDialog.Builder(this).setTitle("🔌 Conectar Depuração Wi-Fi").setView(layout)
            .setPositiveButton("Conectar") { _, _ ->
                val ipPorta = etIP.text.toString().trim()
                if (!ipPorta.contains(":")) {
                    Toast.makeText(this, "Formato inválido! Use IP:Porta", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                tvResultado.text = "Conectando..."
                thread {
                    val r = conectarViaTCP(ipPorta)
                    runOnUiThread {
                        tvResultado.text = r
                        if (r.contains("✅")) {
                            prefs.edit().putString("ip", ipPorta).apply()
                            atualizarStatus()
                            Toast.makeText(this, "Conectado!", Toast.LENGTH_SHORT).show()
                            // Fecha o dialog após 1.5s
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    (layout.parent.parent as? AlertDialog)?.dismiss()
                                } catch (_: Exception) {}
                            }, 1500)
                        }
                    }
                }
            }
            .setNegativeButton("Fechar", null).show()
    }

    private fun conectarViaTCP(ipPorta: String): String {
        return try {
            val partes = ipPorta.split(":")
            val ip = partes[0]
            val porta = partes.getOrNull(1)?.toIntOrNull() ?: 5555

            val socket = Socket(ip, porta)
            socket.soTimeout = 5000

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            // Testa conexão executando um comando simples
            writer.println("wm size")
            val resposta = reader.readLine()

            socket.close()

            if (resposta != null && resposta.contains("Physical size")) {
                // Salva o IP para comandos futuros
                prefs.edit().putString("ip_conectado", ipPorta).apply()
                "✅ Conectado! Resolução atual: $resposta"
            } else {
                "❌ Não foi possível executar comandos. Verifique se a depuração está ativa."
            }
        } catch (e: Exception) {
            "❌ Erro: ${e.message?.take(50) ?: "Falha na conexão"}"
        }
    }

    private fun executarComandoRemoto(cmd: String): String {
        return try {
            val ipPorta = prefs.getString("ip_conectado", "") ?: return "Sem conexão"
            val partes = ipPorta.split(":")
            val ip = partes[0]
            val porta = partes.getOrNull(1)?.toIntOrNull() ?: 5555

            val socket = Socket(ip, porta)
            socket.soTimeout = 5000

            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            writer.println(cmd)
            val resposta = reader.readLine()
            socket.close()
            resposta ?: "OK"
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    private fun restaurarResolucao() {
        try {
            // Tenta local primeiro
            Runtime.getRuntime().exec("wm size reset")
            Runtime.getRuntime().exec("wm density reset")
        } catch (_: Exception) {
            // Se falhar, tenta remoto
            thread {
                executarComandoRemoto("wm size reset")
                executarComandoRemoto("wm density reset")
            }
        }
        atualizarStatus()
    }

    private fun atualizarStatus() {
        val ok = temPermissaoShell() || prefs.getString("ip_conectado", "").isNotEmpty()
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
    private var largura = 720; private var altura = 1280; private var dpi = 280
    private var ativo = false; private var jogoAtivo = false
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("perfis", MODE_PRIVATE)
    }

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
        ativo = true; mostrarNotificacao()
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
        try {
            Runtime.getRuntime().exec(cmd).waitFor()
        } catch (_: Exception) {
            // Tenta via TCP se local falhar
            executarRemoto(cmd)
        }
    }

    private fun executarRemoto(cmd: String) {
        try {
            val ipPorta = prefs.getString("ip_conectado", "") ?: return
            val partes = ipPorta.split(":")
            val socket = Socket(partes[0], partes.getOrNull(1)?.toIntOrNull() ?: 5555)
            socket.soTimeout = 3000
            PrintWriter(socket.getOutputStream(), true).println(cmd)
            socket.close()
        } catch (_: Exception) {}
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
            .setContentTitle("Game Resolution").setContentText("Monitorando: $pacote")
            .setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true).build())
    }

    override fun onBind(i: Intent?) = null
    override fun onDestroy() { ativo = false; restaurar(); super.onDestroy() }
    override fun onTaskRemoved(r: Intent?) { ativo = false; restaurar(); stopSelf() }
}
