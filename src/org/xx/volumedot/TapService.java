package org.xx.volumedot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * 无障碍手势服务，两个用途：
 *   1. 补一次点按，点开音量面板的“展开”箭头；
 *   2. 双击时截一张屏，从画面里认出音量面板那几条竖滑块，把“正在播放应用”那条往下拖 10%。
 *
 * 为什么要截图：vivo 没有公开的逐应用音量 API，只能像人一样去拖面板上的那条滑块，
 * 而拖多少要先知道自己现在在哪一格——所以只能截图量。
 */
public class TapService extends AccessibilityService {

    private static final String TAG = "VolumeDot";
    public static volatile TapService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "TapService connected");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    public void tap(final int x, final int y, long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Path p = new Path();
                    p.moveTo(x, y);
                    p.lineTo(x + 1, y);
                    GestureDescription.Builder b = new GestureDescription.Builder();
                    b.addStroke(new GestureDescription.StrokeDescription(p, 0, 40));
                    boolean ok = dispatchGesture(b.build(), null, null);
                    Log.i(TAG, "dispatchGesture(" + x + "," + y + ") ok=" + ok);
                } catch (Throwable t) {
                    Log.w(TAG, "dispatchGesture failed", t);
                }
            }
        }, delayMs);
    }

    /** 拖一段：从 (x,y1) 竖直拖到 (x,y2)。 */
    public boolean swipe(final int x, final int y1, final int y2, int durationMs) {
        try {
            Path p = new Path();
            p.moveTo(x, y1);
            p.lineTo(x, y2);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(p, 0, durationMs));
            boolean ok = dispatchGesture(b.build(), null, null);
            Log.i(TAG, "swipe(" + x + "," + y1 + "->" + y2 + ") ok=" + ok);
            return ok;
        } catch (Throwable t) {
            Log.w(TAG, "swipe failed", t);
            return false;
        }
    }

    /**
     * 优先按无障碍窗口信息算“展开”箭头坐标（面板贴右边、窄而高那个窗口）。
     * 拿不到窗口就返回 null，由调用方用常量兜底。
     */
    public int[] findVolumeChevron() {
        try {
            java.util.List<android.view.accessibility.AccessibilityWindowInfo> wins = getWindows();
            if (wins == null) {
                return null;
            }
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int bestTop = -1;
            int bestX = -1;
            for (android.view.accessibility.AccessibilityWindowInfo wi : wins) {
                android.graphics.Rect r = new android.graphics.Rect();
                wi.getBoundsInScreen(r);
                if (r.right >= screenW - 2
                        && r.width() > 0 && r.width() < screenW / 2
                        && r.height() > screenH / 6 && r.height() < screenH) {
                    if (r.top > bestTop) {
                        bestTop = r.top;
                        bestX = r.centerX();
                    }
                }
            }
            if (bestTop >= 0) {
                float d = getResources().getDisplayMetrics().density;
                return new int[]{bestX, bestTop + (int) (62 * d)};
            }
        } catch (Throwable t) {
            Log.w(TAG, "findVolumeChevron failed", t);
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // 双击：把“正在播放的那个应用”（抖音）那条滑块往下拖 delta
    // ---------------------------------------------------------------------

    public void lowerPlayingAppVolume(final float delta) {
        if (Build.VERSION.SDK_INT < 30) {
            toast("系统版本过低，双击调音量不可用");
            return;
        }
        mPass = 0;
        mDesiredV = -1f;
        captureOnce(delta);
    }

    private float mDesiredV = -1f;
    private int mPass = 0;

    /** 截一帧，量一次，必要时补一刀（拖滑块取滑块时有几像素误差，第一次往往拖不满）。 */
    private void captureOnce(final float delta) {
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            Bitmap argb = null;
                            HardwareBuffer hb = null;
                            try {
                                hb = result.getHardwareBuffer();
                                Bitmap hw = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
                                if (hw != null) {
                                    argb = hw.copy(Bitmap.Config.ARGB_8888, false);
                                    hw.recycle();
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "decode screenshot failed", t);
                            } finally {
                                if (hb != null) {
                                    try {
                                        hb.close();
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                            if (argb != null) {
                                analyzeAndAdjust(argb, delta);
                                argb.recycle();
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            Log.w(TAG, "takeScreenshot failed: " + errorCode);
                            toast("截图失败(" + errorCode + ")：请在系统设置里重新开关一次本应用的无障碍");
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "takeScreenshot threw", t);
            toast("截图不可用：" + t);
        }
    }

    /** 截图里找音量面板的竖滑块，定位“正在播放应用”那一条，往下拖 delta（0~1）。 */
    private void analyzeAndAdjust(Bitmap bmp, float delta) {
        final int w = bmp.getWidth();
        final int h = bmp.getHeight();
        final int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        // 1) 逐列统计“滑块蓝”与“轨道灰”的像素，竖滑块就是这两类颜色堆出来的长条
        int[] cnt = new int[w];
        int stepY = 4;
        for (int y = 0; y < h; y += stepY) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int c = px[row + x];
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                if (isSliderBlue(r, g, b) || isSliderGray(r, g, b)) {
                    cnt[x]++;
                }
            }
        }

        // 2) 连续的高像素列 → 一条滑块
        float density = getResources().getDisplayMetrics().density;
        int minW = (int) (36 * density), maxW = (int) (130 * density);
        int thr = (int) ((h / stepY) * 0.045f);
        java.util.ArrayList<int[]> bands = new java.util.ArrayList<>();
        int start = -1;
        for (int x = 0; x < w; x++) {
            boolean on = cnt[x] >= thr;
            if (on && start < 0) {
                start = x;
            } else if (!on && start >= 0) {
                int bw = x - start;
                if (bw >= minW && bw <= maxW) {
                    bands.add(new int[]{start, x - 1});
                }
                start = -1;
            }
        }
        if (start >= 0) {
            int bw = w - start;
            if (bw >= minW && bw <= maxW) {
                bands.add(new int[]{start, w - 1});
            }
        }
        Log.i(TAG, "detected bands=" + bands.size());

        // 3) 只保留等间距的、而且至少 4 条（闹钟/通知/铃声/媒体 四路打底）
        java.util.ArrayList<int[]> use = pickEvenlySpaced(bands);
        if (use.size() < 5) {
            toast("面板里只有 " + use.size() + " 条滑块：此刻没有应用在播放（抖音在播时才会多一条）");
            return;
        }

        // 4) 逐条量竖直范围：几条滑轨一定是同高同底的，取中位数最稳
        java.util.ArrayList<Integer> tops = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> bottoms = new java.util.ArrayList<>();
        for (int[] band : use) {
            int[] ext = verticalExtent(px, w, h, band[0], band[1]);
            if (ext != null && ext[1] - ext[0] > (int) (100 * density)) {
                tops.add(ext[0]);
                bottoms.add(ext[1]);
            }
        }
        if (tops.size() < 3) {
            toast("没量到滑轨范围，双击调音量已跳过");
            return;
        }
        java.util.Collections.sort(tops);
        java.util.Collections.sort(bottoms);
        int trackTop = tops.get(tops.size() / 2);
        int trackBottom = bottoms.get(bottoms.size() / 2);

        // 最右边那条固定是“媒体”，它左边那条就是正在出声的应用（抖音）
        int[] target = use.get(use.size() - 2);
        int cx = (target[0] + target[1]) / 2;
        int[] ext = verticalExtent(px, w, h, target[0], target[1]);

        if (ext == null || trackBottom - trackTop < (int) (120 * density)) {
            toast("没量到滑块范围，双击调音量已跳过");
            return;
        }
        int fillTop = ext[2];
        if (fillTop < 0) {
            toast("那条滑块读不到当前位置（音量可能是 0），已跳过");
            return;
        }
        float cur = (trackBottom - fillTop) / (float) (trackBottom - trackTop);
        // 第一刀：按当前值算目标；第二刀：沿用第一刀的目标值，只补偏差
        float desired = (mPass == 0) ? Math.max(cur - delta, 0.02f) : mDesiredV;
        if (desired < 0) {
            desired = Math.max(cur - delta, 0.02f);
        }
        mDesiredV = desired;
        int y2 = Math.round(trackBottom - desired * (trackBottom - trackTop));
        Log.i(TAG, "pass=" + mPass + " target x=" + cx + " cur=" + cur + " -> " + desired
                + " track=" + trackTop + ".." + trackBottom + " fillTop=" + fillTop + " y2=" + y2);

        // 5) 从当前滑块位置往下拖到目标位置（起点就是滑块，等于抓住它拖）
        int y1 = Math.max(fillTop + 2, trackTop + 4);
        int tol = (int) (5 * density);
        if (mPass == 0) {
            boolean ok = swipe(cx, y1, y2, 320);
            if (!ok) {
                toast("调音量手势没能发出");
                return;
            }
            mPass = 1;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    captureOnce(delta);
                }
            }, 550);
        } else {
            if (Math.abs(fillTop - y2) > tol) {
                // 还差一点，再补一刀（拖滑块取滑块时有几像素死区）
                swipe(cx, y1, y2, 260);
            }
            toast("已把正在播放应用的音量调低约 10%");
        }
    }

    /** 从候选里挑一组等间距的滑块（容忍少量误检）。 */
    private java.util.ArrayList<int[]> pickEvenlySpaced(java.util.ArrayList<int[]> bands) {
        int n = bands.size();
        java.util.ArrayList<int[]> best = new java.util.ArrayList<>();
        for (int s = 0; s < n; s++) {
            java.util.ArrayList<int[]> cur = new java.util.ArrayList<>();
            cur.add(bands.get(s));
            int lastCenter = (bands.get(s)[0] + bands.get(s)[1]) / 2;
            int pitch = -1;
            for (int j = s + 1; j < n; j++) {
                int c = (bands.get(j)[0] + bands.get(j)[1]) / 2;
                int d = c - lastCenter;
                if (pitch < 0) {
                    if (d > 0) {
                        pitch = d;
                        cur.add(bands.get(j));
                        lastCenter = c;
                    }
                } else if (Math.abs(d - pitch) <= pitch / 3) {
                    cur.add(bands.get(j));
                    lastCenter = c;
                }
            }
            if (cur.size() > best.size()) {
                best = cur;
            }
        }
        return best;
    }

    /**
     * 返回 {滑轨上沿, 滑轨下沿, 蓝色填充最上沿(y，没有则 -1)}。
     * 面板卡片是半透明的，画面里的文字/图标可能撞色，所以不能取“最上面的命中点”，
     * 而取整列里最长的一段连续命中（那才是滑块本身）。
     */
    private int[] verticalExtent(int[] px, int w, int h, int x0, int x1) {
        boolean[] hit = new boolean[h];
        boolean[] blue = new boolean[h];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = x0; x <= x1; x += 3) {
                int c = px[row + x];
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                if (isSliderBlue(r, g, b)) {
                    hit[y] = true;
                    blue[y] = true;
                    break;
                } else if (isSliderGray(r, g, b)) {
                    hit[y] = true;
                }
            }
        }
        int bestS = -1, bestE = -1, runS = -1;
        for (int y = 0; y <= h; y++) {
            boolean on = y < h && (hit[y] || blue[y]);
            if (on && runS < 0) {
                runS = y;
            } else if (!on && runS >= 0) {
                if (bestS < 0 || (y - runS) > (bestE - bestS)) {
                    bestS = runS;
                    bestE = y - 1;
                }
                runS = -1;
            }
        }
        if (bestS < 0) {
            return null;
        }
        int fillTop = -1;
        for (int y = bestS; y <= bestE; y++) {
            if (blue[y]) {
                fillTop = y;
                break;
            }
        }
        return new int[]{bestS, bestE, fillTop};
    }

    private boolean isSliderBlue(int r, int g, int b) {
        return b > 185 && r < 95 && g > 85 && g < 205 && (b - g) > 50;
    }

    private boolean isSliderGray(int r, int g, int b) {
        int mx = Math.max(r, Math.max(g, b));
        int mn = Math.min(r, Math.min(g, b));
        return mx - mn < 14 && r >= 92 && r <= 128;
    }

    private void toast(final String s) {
        Log.i(TAG, s);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(TapService.this, s, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
