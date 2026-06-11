package com.gameresolution;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    EditText etPacote, etLargura, etAltura, etDpi;
    Button btnIniciar, btnParar, btnRestaurar;
    TextView tvStatus;
    Intent serviceIntent;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        prefs = getSharedPreferences("game_res", MODE_PRIVATE);
        restaurarAgora();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        tvStatus = new TextView(this);
        tvStatus.setText("Status: Parado");
        tvStatus.setTextColor(0xFF666666);
        layout.addView(tvStatus);

        etPacote = new EditText(this);
        etPacote.setHint("Pacote do jogo");
        etPacote.setText(prefs.getString("pacote", ""));
        layout.addView(etPacote);

        LinearLayout resLayout = new LinearLayout(this);
        resLayout.setOrientation(LinearLayout.HORIZONTAL);

        etLargura = new EditText(this);
        etLargura.setHint("720");
        etLargura.setInputType(InputType.TYPE_CLASS_NUMBER);
        etLargura.setText(prefs.getString("largura", "720"));
        resLayout.addView(etLargura);

        TextView txtX = new TextView(this);
        txtX.setText(" x ");
        resLayout.addView(txtX);

        etAltura = new EditText(this);
        etAltura.setHint("1280");
        etAltura.setInputType(InputType.TYPE_CLASS_NUMBER);
        etAltura.setText(prefs.getString("altura", "1280"));
        resLayout.addView(etAltura);

        layout.addView(resLayout);

        etDpi = new EditText(this);
        etDpi.setHint("280");
        etDpi.setInputType(InputType.TYPE_CLASS_NUMBER);
        etDpi.setText(prefs.getString("dpi", "280"));
        layout.addView(etDpi);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);

        btnIniciar = new Button(this);
        btnIniciar.setText("INICIAR");
        btnIniciar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iniciar();
            }
        });
        btnLayout.addView(btnIniciar);

        btnParar = new Button(this);
        btnParar.setText("PARAR");
        btnParar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parar();
            }
        });
        btnLayout.addView(btnParar);

        layout.addView(btnLayout);

        btnRestaurar = new Button(this);
        btnRestaurar.setText("RESTAURAR");
        btnRestaurar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restaurarAgora();
                Toast.makeText(MainActivity.this, "Resolução restaurada!", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnRestaurar);

        setContentView(layout);
        serviceIntent = new Intent(this, GameService.class);

        if (!temPermissao()) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    void iniciar() {
        String pacote = etPacote.getText().toString().trim();
        if (pacote.isEmpty()) {
            Toast.makeText(this, "Digite o pacote do jogo!", Toast.LENGTH_SHORT).show();
            return;
        }

        int w = parseInt(etLargura.getText().toString(), 720);
        int h = parseInt(etAltura.getText().toString(), 1280);
        int d = parseInt(etDpi.getText().toString(), 280);

        prefs.edit()
            .putString("pacote", pacote)
            .putString("largura", String.valueOf(w))
            .putString("altura", String.valueOf(h))
            .putString("dpi", String.valueOf(d))
            .putBoolean("ativo", true)
            .apply();

        serviceIntent.putExtra("pacote", pacote);
        serviceIntent.putExtra("largura", w);
        serviceIntent.putExtra("altura", h);
        serviceIntent.putExtra("dpi", d);
        serviceIntent.setAction("INICIAR");

        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        tvStatus.setText("Status: Monitorando");
        Toast.makeText(this, "Monitorando: " + pacote, Toast.LENGTH_SHORT).show();
    }

    void parar() {
        serviceIntent.setAction("PARAR");
        startService(serviceIntent);
        prefs.edit().putBoolean("ativo", false).apply();
        tvStatus.setText("Status: Parado");
        restaurarAgora();
    }

    void restaurarAgora() {
        try {
            Runtime.getRuntime().exec("wm size reset");
            Runtime.getRuntime().exec("wm density reset");
        } catch (Exception e) {}
        prefs.edit().putBoolean("resolucao_alterada", false).apply();
    }

    int parseInt(String s, int padrao) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return padrao;
        }
    }

    boolean temPermissao() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int modo = ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), getPackageName());
            return modo == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.getBoolean("resolucao_alterada", false)) {
            restaurarAgora();
        }
    }
  }
