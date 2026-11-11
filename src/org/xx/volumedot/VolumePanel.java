package org.xx.volumedot;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.util.Log;

/**
 * 唤起音量面板的两种途径，实测择一使用。
 *
 * A) showClassic()：走 AudioManager + FLAG_SHOW_UI，触发 SystemUI 的经典音量面板
 *    （音量键唤出的那种：右侧细条，点 "<" 展开成多路/逐应用滑块卡片）。
 * B) showDialog()：给 SystemUI 的 VolumePanelDialogReceiver 发广播，
 *    打开的是"音量设置"式弹窗（媒体/通话/铃声/通知/闹钟 + 设置/完成）。
 */
public class VolumePanel {

    private static final String TAG = "VolumeDot";

    public static final String ACTION_LAUNCH = "com.android.systemui.action.LAUNCH_VOLUME_PANEL_DIALOG";
    public static final String ACTION_LAUNCH_ALT = "android.settings.panel.action.VOLUME";
    public static final String SYSTEMUI_PKG = "com.android.systemui";
    public static final String RECEIVER_CLS = "com.android.systemui.volume.VolumePanelDialogReceiver";

    /** A：经典音量面板（FLAG_SHOW_UI）。返回是否没抛异常。 */
    public static boolean showClassic(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return false;
        }
        try {
            int idx = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            // vivo 实测：值不变时 SystemUI 不弹面板，所以先推到相邻一格再回原值
            int tmp = idx < max ? idx + 1 : idx - 1;
            if (tmp < 0) {
                tmp = 0;
            }
            am.setStreamVolume(AudioManager.STREAM_MUSIC, tmp, AudioManager.FLAG_SHOW_UI);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0);
            Log.i(TAG, "classic(nudge) idx=" + idx + " tmp=" + tmp);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "setStreamVolume failed", t);
        }
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME,
                    AudioManager.FLAG_SHOW_UI);
            Log.i(TAG, "classic(adjustStreamVolume)");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "adjust failed", t);
        }
        return false;
    }

    /** B：音量设置式弹窗（广播）。 */
    public static boolean showDialog(Context ctx) {
        boolean sent = false;
        try {
            Intent i = new Intent(ACTION_LAUNCH);
            i.setComponent(new ComponentName(SYSTEMUI_PKG, RECEIVER_CLS));
            i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            ctx.sendBroadcast(i);
            sent = true;
            Log.i(TAG, "broadcast explicit -> " + RECEIVER_CLS);
        } catch (Throwable t) {
            Log.w(TAG, "explicit broadcast failed", t);
        }
        try {
            Intent i2 = new Intent(ACTION_LAUNCH);
            i2.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            ctx.sendBroadcast(i2);
        } catch (Throwable t) {
            Log.w(TAG, "implicit broadcast failed", t);
        }
        try {
            Intent i3 = new Intent(ACTION_LAUNCH_ALT);
            i3.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            ctx.sendBroadcast(i3);
        } catch (Throwable t) {
            Log.w(TAG, "alt broadcast failed", t);
        }
        return sent;
    }
}
