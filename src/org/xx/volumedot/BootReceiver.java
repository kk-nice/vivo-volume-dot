package org.xx.volumedot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** 开机后自动把小白点拉起来（前提是已授予悬浮窗权限）。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Log.i("VolumeDot", "boot completed, start dot service");
        Intent svc = new Intent(context, DotService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Throwable t) {
            Log.w("VolumeDot", "boot start failed", t);
        }
    }
}
