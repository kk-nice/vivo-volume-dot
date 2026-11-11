package org.xx.volumedot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView mStatus;
    private Button mToggle;
    private TextView mSizeLabel;
    private TextView mAlphaLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, dp(32), p, dp(32));
        root.setBackgroundColor(Color.parseColor("#F7F8FA"));

        TextView title = new TextView(this);
        title.setText("音量小白点");
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#15171A"));
        root.addView(title, wrap());

        TextView sub = new TextView(this);
        sub.setText("单击弹 vivo 音量面板（含每个正在发声应用单独一条滑块）；双击把正在播放的应用（抖音）调低 10；长按隐藏。");
        sub.setTextSize(13);
        sub.setTextColor(Color.parseColor("#6B7280"));
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        mStatus = new TextView(this);
        mStatus.setTextSize(13);
        mStatus.setTextColor(Color.parseColor("#374151"));
        LinearLayout.LayoutParams stLp = wrap();
        stLp.topMargin = dp(20);
        root.addView(mStatus, stLp);

        Button grant = button("①  授予“显示在其他应用上层”权限");
        grant.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(i);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                }
            }
        });
        LinearLayout.LayoutParams gLp = wrap();
        gLp.topMargin = dp(24);
        root.addView(grant, gLp);

        mToggle = button("②  开启小白点");
        mToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!DotService.isRunning) {
                    if (!canDrawOverlays()) {
                        Toast.makeText(MainActivity.this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Intent svc = new Intent(MainActivity.this, DotService.class);
                    if (Build.VERSION.SDK_INT >= 26) {
                        startForegroundService(svc);
                    } else {
                        startService(svc);
                    }
                } else if (DotService.isDotVisible()) {
                    startService(new Intent(MainActivity.this, DotService.class)
                            .setAction(DotService.ACTION_HIDE));
                } else {
                    startService(new Intent(MainActivity.this, DotService.class)
                            .setAction(DotService.ACTION_SHOW));
                }
                refresh();
                mStatus.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }, 600);
            }
        });
        root.addView(mToggle, wrap());

        Button testA = button("③ 开启/关闭“无障碍手势”（自动展开 + 双击调音量）");
        testA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    Toast.makeText(MainActivity.this, "请在列表里打开“音量小白点”的无障碍开关", Toast.LENGTH_LONG).show();
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "打开无障碍设置失败：" + t, Toast.LENGTH_LONG).show();
                }
            }
        });
        LinearLayout.LayoutParams tLp = wrap();
        tLp.topMargin = dp(12);
        root.addView(testA, tLp);

        // ---- 外观：大小 / 透明度 ----
        TextView capLabel = new TextView(this);
        capLabel.setText("外观");
        capLabel.setTextSize(15);
        capLabel.setTextColor(Color.parseColor("#15171A"));
        LinearLayout.LayoutParams capLp = wrap();
        capLp.topMargin = dp(26);
        root.addView(capLabel, capLp);

        mSizeLabel = new TextView(this);
        mSizeLabel.setTextSize(13);
        mSizeLabel.setTextColor(Color.parseColor("#374151"));
        LinearLayout.LayoutParams l1 = wrap();
        l1.topMargin = dp(12);
        root.addView(mSizeLabel, l1);

        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(80 - 24);
        sizeBar.setProgress(DotService.getSizeDp(this) - 24);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int size = 24 + progress;
                saveInt(DotService.K_SIZE, size);
                showSizeLabel(size);
                if (fromUser) {
                    DotService.applyStyle();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        root.addView(sizeBar, wrap());
        showSizeLabel(DotService.getSizeDp(this));

        mAlphaLabel = new TextView(this);
        mAlphaLabel.setTextSize(13);
        mAlphaLabel.setTextColor(Color.parseColor("#374151"));
        LinearLayout.LayoutParams l2 = wrap();
        l2.topMargin = dp(10);
        root.addView(mAlphaLabel, l2);

        SeekBar alphaBar = new SeekBar(this);
        alphaBar.setMax(100 - 15);
        alphaBar.setProgress(DotService.getAlphaPct(this) - 15);
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int a = 15 + progress;
                saveInt(DotService.K_ALPHA, a);
                showAlphaLabel(a);
                if (fromUser) {
                    DotService.applyStyle();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        root.addView(alphaBar, wrap());
        showAlphaLabel(DotService.getAlphaPct(this));

        Button testB = button("测试：直接弹一次音量面板");
        testB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean ok = VolumePanel.showClassic(MainActivity.this);
                Toast.makeText(MainActivity.this, ok ? "已请求音量面板" : "请求失败", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams bLp = wrap();
        bLp.topMargin = dp(24);
        root.addView(testB, bLp);

        TextView tips = new TextView(this);
        tips.setText("说明：\n"
                + "· 单击：弹面板（会自动展开成竖滑块卡片）。\n"
                + "· 双击：弹面板后，把正在播放那个应用（抖音）的音量往下调 10。\n"
                + "· 长按：隐藏小白点（服务还在，从通知栏或本页的按钮可以恢复）。\n"
                + "· 拖动：换位置，位置会记住；大小与透明度在这里调，实时生效。\n"
                + "· 逐应用滑块只在某个应用正在出声时才出现。\n"
                + "· 想常驻不被杀：设置 → 应用与权限 → 权限管理 → 自启动/后台高耗电，允许本应用。");
        tips.setTextSize(12);
        tips.setTextColor(Color.parseColor("#8A8F98"));
        tips.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams tiLp = wrap();
        tiLp.topMargin = dp(24);
        root.addView(tips, tiLp);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void showSizeLabel(int size) {
        mSizeLabel.setText("小白点大小：" + size + " dp（约 "
                + Math.round(size * getResources().getDisplayMetrics().density) + " 像素）");
    }

    private void showAlphaLabel(int a) {
        mAlphaLabel.setText("小白点透明度：" + a + "%");
    }

    private void saveInt(String key, int value) {
        SharedPreferences sp = getSharedPreferences(DotService.PREFS, Context.MODE_PRIVATE);
        sp.edit().putInt(key, value).apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean overlay = canDrawOverlays();
        boolean running = DotService.isRunning;
        boolean visible = DotService.isDotVisible();
        boolean acc = isAccessibilityOn();
        mStatus.setText("悬浮窗权限：" + (overlay ? "已授予" : "未授予")
                + "    小白点：" + (running ? (visible ? "显示中" : "已隐藏") : "未运行")
                + "\n无障碍手势：" + (acc ? "已开启" : "未开启（自动展开/双击调音量需要它）"));
        if (!running) {
            mToggle.setText("②  开启小白点");
        } else if (visible) {
            mToggle.setText("隐藏小白点（长按圆点也可以）");
        } else {
            mToggle.setText("显示小白点");
        }
    }

    private boolean isAccessibilityOn() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) {
                return false;
            }
            return s.contains(getPackageName() + "/" + TapService.class.getName())
                    || s.contains(getPackageName() + "/.TapService");
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        return b;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
