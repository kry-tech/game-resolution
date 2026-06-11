package com.gameresolution;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try {
                Runtime.getRuntime().exec("wm size reset");
                Runtime.getRuntime().exec("wm density reset");
            } catch (Exception e) {}

            SharedPreferences prefs = context.getSharedPreferences("game_res", Context.MODE_PRIVATE);
            prefs.edit()
                .putBoolean("ativo", false)
                .putBoolean("resolucao_alterada", false)
                .apply();
        }
    }
}
