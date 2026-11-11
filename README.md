# 音量小白点（vivo V2507A / Android 16 实测可用）

点一下屏幕上的白色小圆点，直接弹出 vivo 原生音量面板的**展开态**：
闹钟 / 通知 / 铃声 / 媒体 + **当前正在出声的应用各自一条竖滑块**（如抖音），每个应用单独调音量。

## v2 新增（本版四项改动）

| 需求 | 实现 | 状态 |
| --- | --- | --- |
| 小白点尺寸可调 | App 内滑块 24–80dp，拖动实时生效，重启记住 | 实测通过 |
| 小白点透明度可调 | App 内滑块 15%–100%，实时生效，记住 | 实测通过 |
| 长按 = 隐藏（不再关闭） | 长按 0.65s 只把圆点藏起来，服务继续跑；通知栏「显示小白点」按钮或 App 内按钮一键恢复 | 实测通过 |
| 双击 = 抖音 -10 | 双击直接把当前发声的媒体音量降 10%（抖音在播时就是抖音），同时弹面板给你看结果 | 实测 30 → 15 |

## 用法

1. 安装 `音量小白点.apk`
2. 打开 App，点 **① 授予"显示在其他应用上层"权限** → 允许
3. 点 **② 开启小白点** → 出现白色圆点（可拖动，位置记住）
4. 点 **③ 开启"无障碍手势"** → 在系统无障碍列表里把「音量小白点」打开
   （不开也能用：点小白点先弹折叠细条，再点那条上的 `‹` 展开；开了就一步到位展开）
5. 想调大小/透明度：在 App 里拖两个滑块即可，球会实时变化

## 关键前置（防止被系统杀掉）

- 设置 → 应用与权限 → 权限管理 → **自启动**：允许「音量小白点」
- 设置 → 电池 → **后台高耗电**：允许「音量小白点」
- 重启后会自动拉起（已内置开机广播接收器）

## 原理（实测记录）

| 环节 | 走通的路径 |
| --- | --- |
| 唤起面板 | `AudioManager.setStreamVolume(STREAM_MUSIC, 值, FLAG_SHOW_UI)`；**值必须变**（值不变不弹），且**必须紧跟点击同步调用**（延后 300ms 就完全不弹） |
| 面板形态 | 唤起时先出**折叠态**（贴右边细条 294×1019，窗口 `BriefDialog`，type=2020 VOLUME_OVERLAY） |
| 展开 | 折叠条顶部 `‹` 箭头，点它才展开成竖滑块卡片；坐标约 `(屏宽-42dp, 187dp)`，实测 1.3–1.8s 展开完成 |
| 自动点箭头 | 应用内 `AccessibilityService` + `dispatchGesture`，700ms 后补一次点按 |
| 逐应用滑块 | SystemUI 自己生成：某应用正在出声（持媒体焦点）时才多一条；第三方无法增删 |

走过但不通的路径（供以后排障）：

- `Settings$AppVolumeAdjustIndividualActivity`（设置里的"应用音量单独调节"页）受 `com.vivo.settings.permission.START_SETTINGS_ACTIVITY`（signature|privileged）保护，**adb shell 和第三方应用都拉不起来**（SecurityException）。
- `com.android.systemui.action.LAUNCH_VOLUME_PANEL_DIALOG` 广播拉起来的是另一套"音量设置"弹窗，不是逐应用界面。
- 普通应用无法注入音量键事件（需 Shizuku/root）。
- 全量扫过 framework/services/ext/SystemUI 的 dex，**本机没有任何第三方可调用的逐应用音量 API**（`setVolumeForApp` / `AppVolume` 等零命中），所以"只把抖音那一条调低"只能靠 UI 拖面板滑块。

## v2 为什么双击改成"降媒体音量"而不是"拖抖音那条滑块"

原设计：第一下弹面板 → 第二下在面板里把抖音那条滑块拖低 10%。实测被 vivo 两条硬限制卡死：

1. 面板窗口盖在最上层，**第二下点击落在面板外会把面板关掉**（面板几秒后也会自动消失）；
2. `FLAG_SHOW_UI` **只有紧跟点击才能弹面板**，延后（300ms / 450ms 都试过）完全不弹；关闭动画期间再弹也被忽略。

两条叠加 = "双击既要让球收到第二下、又要有面板可拖"没法同时成立。所以双击改为：直接 `setStreamVolume(STREAM_MUSIC, 当前 - 10%, FLAG_SHOW_UI)`。
抖音是唯一在播的媒体时，效果就等于"把抖音调低 10"，而且面板会同时弹出来给你看到滑块确实降了。
要精确到"只动抖音、不碰别的应用"，请在单击弹出的面板里手动拖抖音那条（面板支持逐应用）。

## 已知限制

- 展开箭头位置按本机 1260×2800 / 560dpi 实测，代码先用无障碍窗口信息推测，猜不到回落常量；换机型或横屏可能要微调。
- 逐应用滑块只在应用**正在播声音**时出现（抖音要处于播放状态，暂停时只有四条）。
- 面板弹出有 1.3s 左右延迟，且几秒后自动关闭；想连续操作要快。
- "双击 -10" 按满量程 10% 换算（本机媒体 0–150，一格=15=10%），所以要更细的档位可以改成 1 格或 5%。

## 重新编译

```
.\build.ps1
```

依赖本机 `C:\Users\xx-my1-\Android\Sdk`（platforms;android-30、build-tools;35.0.0）+ JDK 21，全程不走 Gradle（aapt2 → javac → d8 → zipalign → apksigner）。
签名用项目根目录的 `keystore.jks`（口令 android），**不要删**，删了会变成新签名导致覆盖安装失败。

代码结构：

```
src/org/xx/volumedot/MainActivity.java   界面：权限引导 + 尺寸/透明度滑块 + 隐藏/显示
src/org/xx/volumedot/DotService.java     悬浮点（前台服务 + 拖拽 + 单击/长按/双击 + 位置与外观记忆）
src/org/xx/volumedot/VolumePanel.java    唤起面板（nudge + FLAG_SHOW_UI）
src/org/xx/volumedot/TapService.java     无障碍：手势补点展开、截屏识别滑块（逐应用拖拽用）
src/org/xx/volumedot/BootReceiver.java   开机自启
```

调试日志：`/sdcard/Android/data/org.xx.volumedot/files/log/trace.txt`（点击事件轨迹）。
