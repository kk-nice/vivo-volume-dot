package org.xx.volumedot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * 悬浮小白点。
 *
 * 手势约定：
 *   单击   → 弹出音量面板并自动展开
 *   双击   → 同上，撒手后再把“正在播放那个应用”（比如抖音）的音量往下调 10
 *   长按   → 隐藏小白点（服务与通知保留，从通知或 App 里可恢复）
 *   拖动   → 换位置（位置会记住）
 *
 * 外观：大小 24~80dp、透明度 15~100%，都在 App 里用滑块调，实时生效。
 */
public class DotService extends Service {

    private static final String TAG = "VolumeDot";
    public static final String CHANNEL_ID = "dot";

    public static final String ACTION_SHOW = "org.xx.volumedot.SHOW_DOT";
    public static final String ACTION_HIDE = "org.xx.volumedot.HIDE_DOT";
    public static final String ACTION_TOGGLE = "org.xx.volumedot.TOGGLE_DOT";

    public static final String PREFS = "cfg";
    public static final String K_SIZE = "size_dp";
    public static final String K_ALPHA = "alpha_pct";
    public static final String K_X = "pos_x";
    public static final String K_Y = "pos_y";

    public static final int DEFAULT_SIZE_DP = 54;
    public static final int DEFAULT_ALPHA = 100;

    public static volatile boolean isRunning = false;
    public static volatile DotService instance;

    private WindowManager mWm;
    private View mDot;
    private WindowManager.LayoutParams mLp;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mLongPressFired;
    private Runnable mLongPress;
    private Runnable mPendingSingle;

    private float mDownRawX, mDownRawY;
    private int mStartX, mStartY;
    private boolean mMoved;
    private long mLastTapUp = 0;

    private int mSizeDp = DEFAULT_SIZE_DP;
    private int mAlphaPct = DEFAULT_ALPHA;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mWm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        loadStyle();
        showDot();
        startForegroundSafely();
        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_SHOW.equals(a)) {
            showDot();
        } else if (ACTION_HIDE.equals(a)) {
            hideDot();
        } else if (ACTION_TOGGLE.equals(a)) {
            if (mDot == null) {
                showDot();
            } else {
                hideDot();
            }
        }
        if (intent != null && a != null) {
            loadStyle();
            if (mDot != null) {
                applyStyleToView();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        instance = null;
        if (mDot != null) {
            try {
                mWm.removeView(mDot);
            } catch (Throwable t) {
                Log.w(TAG, "removeView failed", t);
            }
            mDot = null;
        }
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------- 外观设置 ----------------

    static int getSizeDp(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getInt(K_SIZE, DEFAULT_SIZE_DP);
    }

    static int getAlphaPct(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getInt(K_ALPHA, DEFAULT_ALPHA);
    }

    /** 设置页拖动滑块后调用，实时更新正在显示的圆点。 */
    public static void applyStyle() {
        DotService s = instance;
        if (s == null) {
            return;
        }
        s.loadStyle();
        s.applyStyleToView();
    }

    public static boolean isDotVisible() {
        DotService s = instance;
        return s != null && s.mDot != null;
    }

    private void loadStyle() {
        mSizeDp = getSizeDp(this);
        mAlphaPct = getAlphaPct(this);
    }

    private void applyStyleToView() {
        if (mDot == null) {
            return;
        }
        mLp.width = dp(mSizeDp);
        mLp.height = dp(mSizeDp);
        try {
            mWm.updateViewLayout(mDot, mLp);
        } catch (Throwable ignored) {
        }
        if (mDot instanceof DotView) {
            ((DotView) mDot).setStyle(mSizeDp, mAlphaPct);
        }
    }

    // ---------------- 通知 ----------------

    private void startForegroundSafely() {
        Intent open = new Intent(this, MainActivity.class);
        int piFlag = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlag);

        Notification n = buildNotification(pi, mDot != null);
        try {
            startForeground(1, n);
        } catch (Throwable t) {
            Log.w(TAG, "startForeground failed", t);
        }
    }

    private Notification buildNotification(PendingIntent open, boolean visible) {
        if (Build.VERSION.SDK_INT >= 26) {
            // 用 LOW 而不是 MIN，通知里才能带“显示/隐藏”按钮，被长按藏起来后还能捞回来
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "音量小白点", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            try {
                nm.createNotificationChannel(ch);
            } catch (Throwable ignored) {
            }
        }

        Intent toggle = new Intent(this, DotService.class).setAction(ACTION_TOGGLE);
        PendingIntent piToggle = PendingIntent.getService(this, 2, toggle,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("音量小白点")
                .setContentText(visible ? "运行中 · 单击弹面板，双击抖音 -10，长按隐藏"
                        : "已隐藏 · 点右侧按钮恢复")
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_view,
                        visible ? "隐藏小白点" : "显示小白点",
                        piToggle).build());
        return b.build();
    }

    private void refreshNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.notify(1, buildNotification(pi, mDot != null));
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 悬浮点 ----------------

    private void hideDot() {
        if (mDot == null) {
            return;
        }
        try {
            mWm.removeView(mDot);
        } catch (Throwable ignored) {
        }
        mDot = null;
        mHandler.removeCallbacksAndMessages(null);
        refreshNotification();
        Toast.makeText(this, "已隐藏小白点，可从通知栏或本 App 恢复", Toast.LENGTH_LONG).show();
    }

    private void showDot() {
        if (mDot != null) {
            return;
        }
        loadStyle();
        mDot = new DotView(this);
        ((DotView) mDot).setStyle(mSizeDp, mAlphaPct);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

        mLp = new WindowManager.LayoutParams(
                dp(mSizeDp), dp(mSizeDp), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        mLp.gravity = Gravity.TOP | Gravity.START;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        mLp.x = sp.getInt(K_X, screenW - dp(78));
        mLp.y = sp.getInt(K_Y, screenH - dp(320));

        mDot.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        Log.i(TAG, "touch down");
                        dbg("down");
                        mDownRawX = e.getRawX();
                        mDownRawY = e.getRawY();
                        mStartX = mLp.x;
                        mStartY = mLp.y;
                        mMoved = false;
                        mLongPressFired = false;
                        mLongPress = new Runnable() {
                            @Override
                            public void run() {
                                mLongPressFired = true;
                                vibrate();
                                hideDot();
                            }
                        };
                        mHandler.postDelayed(mLongPress, 650);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - mDownRawX);
                        int dy = (int) (e.getRawY() - mDownRawY);
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) {
                            mMoved = true;
                            if (mLongPress != null) {
                                mHandler.removeCallbacks(mLongPress);
                            }
                        }
                        if (mMoved) {
                            mLp.x = mStartX + dx;
                            mLp.y = mStartY + dy;
                            try {
                                mWm.updateViewLayout(mDot, mLp);
                            } catch (Throwable ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        Log.i(TAG, "touch up moved=" + mMoved + " long=" + mLongPressFired);
                        dbg("up moved=" + mMoved + " long=" + mLongPressFired);
                        if (mLongPress != null) {
                            mHandler.removeCallbacks(mLongPress);
                        }
                        if (mMoved) {
                            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                    .putInt(K_X, mLp.x).putInt(K_Y, mLp.y).apply();
                        } else if (!mLongPressFired) {
                            long now = SystemClock.uptimeMillis();
                            if (mLastTapUp != 0 && now - mLastTapUp < 340
                                    && now - mLastTapUp > 0) {
                                // 第二下：面板已经由第一下弹出，这里只负责把抖音那条拖低
                                mLastTapUp = 0;
                                dbg("double click");
                                onDotDoubleClick();
                            } else {
                                mLastTapUp = now;
                                // 必须“立即”弹：实测 vivo 的 SystemUI 对延后的
                                // setStreamVolume(FLAG_SHOW_UI) 不弹面板
                                onDotClick();
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (mLongPress != null) {
                            mHandler.removeCallbacks(mLongPress);
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            mWm.addView(mDot, mLp);
            refreshNotification();
        } catch (Throwable t) {
            Log.e(TAG, "addView failed", t);
            Toast.makeText(this, "添加悬浮球失败：" + t, Toast.LENGTH_LONG).show();
            isRunning = false;
            stopSelf();
        }
    }

    /** 弹出面板，并在 700ms 后补一次点按去点“展开”箭头。 */
    private void openPanelThenExpand(long expandDelay) {
        boolean ok = VolumePanel.showClassic(this);
        dbg("showClassic=" + ok);
        if (!ok) {
            VolumePanel.showDialog(this);
            return;
        }
        TapService ts = TapService.instance;
        if (ts == null) {
            Toast.makeText(this, "面板已弹出：点右侧细条上的 ‹ 即可展开", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] c = ts.findVolumeChevron();
        int w = getResources().getDisplayMetrics().widthPixels;
        int x = (c != null) ? c[0] : w - dp(42);
        int y = (c != null) ? c[1] : dp(187);
        ts.tap(x, y, expandDelay);
        Log.i(TAG, "auto-expand tap at " + x + "," + y + " (measured=" + (c != null) + ")");
    }

    private void onDotClick() {
        Log.i(TAG, "single click -> open panel");
        dbg("single click");
        vibrate();
        openPanelThenExpand(700);
    }

    /** 调试用：把事件写进外部私有目录，避免 vivo 过滤 logcat。 */
    private void dbg(String s) {
        try {
            java.io.File f = new java.io.File(getExternalFilesDir("log"), "trace.txt");
            f.getParentFile().mkdirs();
            java.io.FileOutputStream os = new java.io.FileOutputStream(f, true);
            os.write((new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()) + " " + s + "\n").getBytes("UTF-8"));
            os.close();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 双击：直接把当前发声的媒体音量（抖音正在播时就是抖音）降 10%，并弹面板给出反馈。
     *
     * 说明：原设计是“第一下弹面板、第二下把面板里抖音那条滑块往下拖”，但实测 vivo 有两条硬限制：
     * 1) 第二下点击落在面板外会把面板关掉；2) setStreamVolume(FLAG_SHOW_UI) 只有紧跟点击时才弹面板，
     *    延后（哪怕 300ms）就完全不弹。两条叠加导致“双击→拖面板滑块”没法稳定成立，因此改为直接降音量。
     */
    private void onDotDoubleClick() {
        vibrate();
        Toast.makeText(this, "已把正在播放的音量调低约 10%", Toast.LENGTH_SHORT).show();
        dbg("double click -> lower stream");
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int idx = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int step = Math.max(1, Math.round(max * 0.10f));
            int target = Math.max(0, idx - step);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            dbg("stream " + idx + " -> " + target + " (max=" + max + ")");
        } catch (Throwable t) {
            dbg("lower stream failed: " + t);
        }
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(20);
            }
        } catch (Throwable ignored) {
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 纯代码绘制的白色圆点，大小与透明度可调。 */
    static class DotView extends FrameLayout {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float r;

        DotView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(Color.TRANSPARENT);
            setBackground(gd);
        }

        void setStyle(int sizeDp, int alphaPct) {
            float d = getResources().getDisplayMetrics().density;
            // 直径占窗口的 78%（和最初 54dp 窗口 / 42dp 直径一致）
            r = sizeDp * 0.39f * d;
            int a = Math.max(15, Math.min(100, alphaPct));
            int fillAlpha = Math.round(255f * a / 100f);
            fill.setColor(Color.argb(fillAlpha, 255, 255, 255));
            ring.setColor(Color.argb(Math.round(90f * a / 100f), 0, 0, 0));
            ring.setStrokeWidth(1f * d);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.drawCircle(cx, cy, r, fill);
            canvas.drawCircle(cx, cy, r, ring);
            super.onDraw(canvas);
        }
    }
}
