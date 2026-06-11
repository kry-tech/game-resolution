package com.gameresolution;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;

public class GameService extends Service {

    String pacote;
    int largura, altura, dpi;
    boolean ativo = false;
    boolean jogoRodando = false;
    SharedPreferences prefs;
    BroadcastReceiver screenReceiver;
    BroadcastReceiver connectivityReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("game_res", MODE_PRIVATE);
        registerSafetyReceivers();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            restaurarESair();
            return START_NOT_STICKY;
        }

        String acao = intent.getStringExtra("action") != null
            ? intent.getStringExtra("action")
            : intent.getAction();

        if ("INICIAR".equals(acao)) {
            pacote = intent.getStringExtra("pacote");
            largura = intent.getIntExtra("largura", 720);
            altura = intent.getIntExtra("altura", 1280);
            dpi = intent.getIntExtra("dpi", 280);
            iniciar();
        } else if ("PARAR".equals(acao)) {
            pararTudo();
        }

        return START_STICKY;
    }

    void iniciar() {
        restaurarResolucao();
        ativo = true;
        mostrarNotificacao();
        prefs.edit().putBoolean("ativo", true).apply();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (ativo) {
                    try {
                        if (!temInternet()) {
                            restaurarResolucao();
                            jogoRodando = false;
                            Thread.sleep(5000);
                            continue;
                        }

                        String appAtual = getAppFrente();

                        if (appAtual == null || appAtual.isEmpty()) {
                            if (jogoRodando) {
                                restaurarResolucao();
                                jogoRodando = false;
                            }
                            Thread.sleep(1000);
                            continue;
                        }

                        boolean jogo = appAtual.contains(pacote);

                        if (jogo && !jogoRodando) {
                            alterarResolucao();
                            jogoRodando = true;
                            prefs.edit().putBoolean("resolucao_alterada", true).apply();
                        } else if (!jogo && jogoRodando) {
                            restaurarResolucao();
                            jogoRodando = false;
                            prefs.edit().putBoolean("resolucao_alterada", false).apply();
                        }

                        Thread.sleep(1500);
                    } catch (Exception e) {
                        restaurarResolucao();
                        jogoRodando = false;
                    }
                }
            }
        }).start();
    }

    void alterarResolucao() {
        try {
            exec("wm size " + largura + "x" + altura);
            exec("wm density " + dpi);
        } catch (Exception e) {
            restaurarResolucao();
        }
    }

    void restaurarResolucao() {
        try {
            exec("wm size reset");
            exec("wm density reset");
            prefs.edit().putBoolean("resolucao_alterada", false).apply();
        } catch (Exception e) {}
    }

    void pararTudo() {
        ativo = false;
        restaurarResolucao();
        jogoRodando = false;
        prefs.edit()
            .putBoolean("ativo", false)
            .putBoolean("resolucao_alterada", false)
            .apply();
        stopForeground(true);
        stopSelf();
    }

    void restaurarESair() {
        restaurarResolucao();
        prefs.edit()
            .putBoolean("ativo", false)
            .putBoolean("resolucao_alterada", false)
            .apply();
        stopSelf();
    }

    String getAppFrente() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long agora = System.currentTimeMillis();
            java.util.List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                agora - 5000,
                agora
            );

            if (stats == null || stats.isEmpty()) return "";

            UsageStats recente = null;
            for (UsageStats s : stats) {
                if (recente == null || s.getLastTimeUsed() > recente.getLastTimeUsed()) {
                    recente = s;
                }
            }

            return recente != null ? recente.getPackageName() : "";
        } catch (Exception e) {
            return "";
        }
    }

    String exec(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream())
            );
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            p.waitFor();
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    boolean temInternet() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    void registerSafetyReceivers() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    restaurarResolucao();
                    jogoRodando = false;
                }
            }
        };
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        connectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                if (!temInternet()) {
                    restaurarResolucao();
                    jogoRodando = false;
                }
            }
        };
        registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    void mostrarNotificacao() {
        String canalId = "game_res";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(
                canalId,
                "Game Resolution",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(canal);
            }
        }

        Notification notif;
        if (Build.VERSION.SDK_INT >= 26) {
            notif = new Notification.Builder(this, canalId)
                .setContentTitle("Game Resolution")
                .setContentText("Protegido: " + pacote)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
        } else {
            notif = new Notification.Builder(this)
                .setContentTitle("Game Resolution")
                .setContentText("Protegido: " + pacote)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
        }

        startForeground(1, notif);
    }

    @Override
    public void onDestroy() {
        pararTudo();
        try {
            if (screenReceiver != null) unregisterReceiver(screenReceiver);
            if (connectivityReceiver != null) unregisterReceiver(connectivityReceiver);
        } catch (Exception e) {}
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        pararTudo();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
                                                                }
