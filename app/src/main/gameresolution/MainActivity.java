package com.gameresolution;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    RecyclerView rvPerfis;
    Button btnAdicionar;
    TextView tvPermissaoStatus;
    List<Perfil> perfis = new ArrayList<>();
    PerfilAdapter adapter;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("perfis", MODE_PRIVATE);

        rvPerfis = findViewById(R.id.rv_perfis);
        btnAdicionar = findViewById(R.id.btn_adicionar);
        tvPermissaoStatus = findViewById(R.id.tv_permissao_status);

        rvPerfis.setLayoutManager(new LinearLayoutManager(this));

        carregarPerfis();

        adapter = new PerfilAdapter(perfis, new PerfilAdapter.OnPerfilClickListener() {
            @Override
            public void onPlayClick(Perfil perfil, int position) {
                togglePerfil(perfil, position);
            }

            @Override
            public void onDeleteClick(Perfil perfil, int position) {
                deletarPerfil(perfil, position);
            }
        });

        rvPerfis.setAdapter(adapter);

        btnAdicionar.setOnClickListener(v -> mostrarDialogNovoPerfil());

        atualizarStatusPermissao();
    }

    void carregarPerfis() {
        perfis.clear();
        try {
            String json = prefs.getString("lista", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Perfil p = new Perfil(
                    obj.getString("id"),
                    obj.getString("nome"),
                    obj.getString("pacote"),
                    obj.getInt("largura"),
                    obj.getInt("altura"),
                    obj.getInt("dpi")
                );
                p.ativo = obj.optBoolean("ativo", false);
                perfis.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void salvarPerfis() {
        try {
            JSONArray arr = new JSONArray();
            for (Perfil p : perfis) {
                JSONObject obj = new JSONObject();
                obj.put("id", p.id);
                obj.put("nome", p.nome);
                obj.put("pacote", p.pacote);
                obj.put("largura", p.largura);
                obj.put("altura", p.altura);
                obj.put("dpi", p.dpi);
                obj.put("ativo", p.ativo);
                arr.put(obj);
            }
            prefs.edit().putString("lista", arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void mostrarDialogNovoPerfil() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_perfil, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etNome = view.findViewById(R.id.et_nome);
        EditText etPacote = view.findViewById(R.id.et_pacote);
        EditText etLargura = view.findViewById(R.id.et_largura);
        EditText etAltura = view.findViewById(R.id.et_altura);
        EditText etDpi = view.findViewById(R.id.et_dpi);
        Button btnCancelar = view.findViewById(R.id.btn_cancelar);
        Button btnSalvar = view.findViewById(R.id.btn_salvar);

        btnCancelar.setOnClickListener(v -> dialog.dismiss());

        btnSalvar.setOnClickListener(v -> {
            String nome = etNome.getText().toString().trim();
            String pacote = etPacote.getText().toString().trim();
            String largura = etLargura.getText().toString().trim();
            String altura = etAltura.getText().toString().trim();
            String dpi = etDpi.getText().toString().trim();

            if (nome.isEmpty() || pacote.isEmpty()) {
                Toast.makeText(this, "Nome e pacote são obrigatórios", Toast.LENGTH_SHORT).show();
                return;
            }

            Perfil p = new Perfil(
                UUID.randomUUID().toString(),
                nome,
                pacote,
                parseInt(largura, 720),
                parseInt(altura, 1280),
                parseInt(dpi, 280)
            );

            perfis.add(p);
            salvarPerfis();
            adapter.notifyItemInserted(perfis.size() - 1);
            dialog.dismiss();
            Toast.makeText(this, "Perfil criado!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    void togglePerfil(Perfil perfil, int position) {
        if (!temPermissaoShell()) {
            mostrarDialogPermissoes();
            return;
        }

        if (!temPermissaoUsageStats()) {
            mostrarDialogPermissoes();
            return;
        }

        if (perfil.ativo) {
            // Parar monitoramento
            Intent si = new Intent(this, GameService.class);
            si.setAction("PARAR");
            si.putExtra("id", perfil.id);
            startService(si);

            perfil.ativo = false;
            salvarPerfis();
            adapter.notifyItemChanged(position);
            Toast.makeText(this, perfil.nome + " parado", Toast.LENGTH_SHORT).show();
        } else {
            // Parar todos os outros
            for (int i = 0; i < perfis.size(); i++) {
                if (perfis.get(i).ativo) {
                    Intent si = new Intent(this, GameService.class);
                    si.setAction("PARAR");
                    si.putExtra("id", perfis.get(i).id);
                    startService(si);
                    perfis.get(i).ativo = false;
                }
            }

            // Iniciar este
            Intent si = new Intent(this, GameService.class);
            si.setAction("INICIAR");
            si.putExtra("id", perfil.id);
            si.putExtra("pacote", perfil.pacote);
            si.putExtra("largura", perfil.largura);
            si.putExtra("altura", perfil.altura);
            si.putExtra("dpi", perfil.dpi);

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(si);
            } else {
                startService(si);
            }

            perfil.ativo = true;
            salvarPerfis();
            adapter.notifyItemChanged(position);
            Toast.makeText(this, "Monitorando: " + perfil.nome, Toast.LENGTH_SHORT).show();
        }
    }

    void deletarPerfil(Perfil perfil, int position) {
        if (perfil.ativo) {
            Intent si = new Intent(this, GameService.class);
            si.setAction("PARAR");
            si.putExtra("id", perfil.id);
            startService(si);
        }

        perfis.remove(position);
        salvarPerfis();
        adapter.notifyItemRemoved(position);
        Toast.makeText(this, perfil.nome + " removido", Toast.LENGTH_SHORT).show();
    }

    void mostrarDialogPermissoes() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_permissoes, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Button btnADB = view.findViewById(R.id.btn_permissao_adb);
        Button btnUsage = view.findViewById(R.id.btn_permissao_usage);
        Button btnFechar = view.findViewById(R.id.btn_fechar);

        btnADB.setOnClickListener(v -> {
            Toast.makeText(this,
                "Conecte ao PC e rode:\nadb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS",
                Toast.LENGTH_LONG).show();
        });

        btnUsage.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        });

        btnFechar.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    void atualizarStatusPermissao() {
        boolean ok = temPermissaoShell() && temPermissaoUsageStats();
        tvPermissaoStatus.setText(ok ? "🟢" : "🔴");
    }

    boolean temPermissaoShell() {
        try {
            Process p = Runtime.getRuntime().exec("wm size");
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    boolean temPermissaoUsageStats() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int modo = ops.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), getPackageName());
            return modo == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    int parseInt(String s, int padrao) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return padrao;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarStatusPermissao();
    }
    }
