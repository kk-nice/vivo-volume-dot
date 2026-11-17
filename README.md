# 音量小白点（vivo V2507A / Android 16 实测可用）

点一下屏幕上的白色小圆点，直接弹出 vivo 原生音量面板的**展开态**：
闹钟 / 通知 / 铃声 / 媒体 + **当前正在出声的应用各自一条竖滑块**（如抖音），每个应用单独调音量。

## v3：白点改由无障碍浮层绘制（本版最重要改动）

v2 时期白点走的是悬浮窗（`TYPE_APPLICATION_OVERLAY`），于是系统 `WindowManagerService` 每次都往通知栏塞一条
**「“音量小白点”正在其他应用的上层显示内容」**。这条通知是**系统发的**，不是 App 发的，所以：

- 应用内关通知渠道没用（`mDeleted=true` 后 vivo 仍重发）；
- `appops set ... POST_NOTIFICATION ignore` 没用（那是 Uid 级的应用通知，不拦系统通道）；
- `cmd notification` 没有 `cancel` / `block-channel` 子命令，无 root 关不掉；
- 上架应用商店也去不掉（这跟侧载无关，只要用悬浮窗就发）。

v3 把白点改成用无障碍服务的 `TYPE_ACCESSIBILITY_OVERLAY` 窗口绘制：

| 项 | v2（悬浮窗） | v3（无障碍浮层） |
| --- | --- | --- |
| 需要的权限 | `SYSTEM_ALERT_WINDOW` + 无障碍 | **只要无障碍** |
| 窗口 type | 2032 `TYPE_APPLICATION_OVERLAY` | 2032 `TYPE_ACCESSIBILITY_OVERLAY` |
| 系统那条提醒 | 每次加窗口就发 | **不再发** |
| 应用信息里的"显示在其他应用上层" | 有这一项 | 这一项消失 |
| 无障碍被关掉时的表现 | 退回悬浮窗还能用 | 白点不显示（打开无障碍自动补画） |

旁证：GKD（纯无障碍浮层方案）在本机通知列表里从来没有这条通道；李跳跳同样不声明悬浮窗、也没有这条通知。

**已验证（vivo V2507A / Android 16 / OriginOS 16）**
- manifest 已删 `SYSTEM_ALERT_WINDOW`；机内 `cmd appops get org.xx.volumedot SYSTEM_ALERT_WINDOW` 变为 `default`；
- 白点窗口在 `dumpsys window windows` 里为 `Window{... u0 org.xx.volumedot type=2032 }`；
- 单击 → SystemUI `ExpandableDialog type=2020` 弹出，逐应用滑块正常；
- 长按 0.65s → `hideDot`，窗口移除，App 内按钮恢复；看门狗不会误补；
- 上述全过程 `cmd notification list` 过滤 `AlertWindow` **始终为空**；
- 关掉再打开无障碍：窗口会被系统摘掉，重新连上后 `resync` 强制重建，白点自动回来（日志 `resync force re-add`）。

## v2 新增（四项改动）

| 需求 | 实现 | 状态 |
| --- | --- | --- |
| 小白点尺寸可调 | App 内滑块 24–80dp，拖动实时生效，重启记住 | 实测通过 |
| 小白点透明度可调 | App 内滑块 15%–100%，实时生效，记住 | 实测通过 |
| 长按 = 隐藏（不再关闭） | 长按 0.65s 只把圆点藏起来，服务继续跑；通知栏「显示小白点」按钮或 App 内按钮一键恢复 | 实测通过 |
| 双击 = 抖音 -10 | 双击直接把当前发声的媒体音量降 10%（抖音在播时就是抖音），同时弹面板给你看结果 | 实测 30 → 15 |

## 用法

1. 安装 `音量小白点.apk`
2. 打开 App，点 **① 开启"无障碍手势"** → 跳到系统无障碍设置，在列表里把「音量小白点」打开
   （白点就靠这个无障碍服务画出来，**不需要悬浮窗权限**）
3. 点 **② 开启小白点** → 出现白色圆点（可拖动，位置记住）
4. （可选）点 **③** 开关"无障碍手势"：打开后单击自动把面板展开成竖滑块卡片、双击才可用
5. 想调大小/透明度：在 App 里拖两个滑块即可，球会实时变化
6. 部分机型首次打开无障碍开关会弹「受限制的设置」，见下一节

## 关键前置（防止被系统杀掉）

- 设置 → 应用与权限 → 权限管理 → **自启动**：允许「音量小白点」
- 设置 → 电池 → **后台高耗电**：允许「音量小白点」
- 重启后会自动拉起（已内置开机广播接收器）

## Android 15 / OriginOS：侧载受限设置（必读）

本 APK 不是从应用商店装的，属于 **sideload（侧载）应用**。Android 15 起，系统默认禁止侧载应用使用高敏权限（无障碍、悬浮窗、使用情况访问、通知监听、短信、默认短信/拨号、设备管理），
开启时会弹「受限制的设置」风险提示，并给出"保持限制 / 解除限制"两个选项。

**注意**：v3 已经不再申请悬浮窗权限，所以「显示在其他应用上层」这一项**已经从受限清单里消失**，
只剩「无障碍」这一项需要在首次开启时过一次（之后一直有效）。

**本机实测结论**
- `pm set-app-allowed-restricted-settings` 这条命令在 vivo V2507A（Android 16）上**被移除了**（返回 `Unknown command`）；
- 真实可用的是 appop：`ACCESS_RESTRICTED_SETTINGS`；
- OriginOS 把「应用信息 → 三点菜单 → 允许受限设置」这个入口**藏掉了**，很多人找不到；
- 解除限制后是**按包名整体放行**，不是逐个权限单独放开。

**一条命令放行（放行后重启不会还原，记录持久化在 /data/system/appops.xml）**
```
adb shell appops set org.xx.volumedot ACCESS_RESTRICTED_SETTINGS allow
```
**复查**
```
adb shell cmd appops get org.xx.volumedot
→ ACCESS_RESTRICTED_SETTINGS: allow
```

**会失效、需要重跑的情况**：卸载重装（覆盖安装 `adb install -r` 一般保留）、恢复出厂、换包名重装。
所以本仓库提供了 `tools/adb-fix.bat`（双击即可），它一次做三件事：

1. 放行受限设置 `ACCESS_RESTRICTED_SETTINGS`
2. 放行悬浮窗 `SYSTEM_ALERT_WINDOW`（系统有时会把它打成 `ignore`）
3. 重新绑定无障碍服务（保留其它已启用的无障碍服务，如 GKD）

命令行用法：`powershell -ExecutionPolicy Bypass -File tools\adb-fix.ps1 -Serial <序列号>`

**关于「"音量小白点"正在其他应用的上层显示内容」这条通知 —— v3 已经彻底没有它了**

它是系统 `WindowManagerService` 发的（通道 id `com.android.server.wm.AlertWindowNotification - org.xx.volumedot`），
触发条件是"应用添加了一个应用级悬浮窗（`TYPE_APPLICATION_OVERLAY`）"，跟侧载、appops、通知权限**都无关**：
`appops set ... POST_NOTIFICATION ignore`、关通道、上架商店都拦不住它。

v3 把白点改成无障碍浮层后，本应用不再持有任何悬浮窗 token，WMS 自然不会再发这条提醒。
实测：打开/关闭/重开白点、单击弹面板全过程，`adb shell cmd notification list` 里都查不到 `AlertWindow` 条目。

## 原理（实测记录）

| 环节 | 走通的路径 |
| --- | --- |
| 画白点 | 无障碍服务（`TapService`）用 `TYPE_ACCESSIBILITY_OVERLAY` 窗口承载 `DotView`，不需要悬浮窗权限 |
| 无障碍没连上/被关 | 不画，记 `mWanted`；服务连上后 `resync()` 强制重建窗口（系统摘窗口时本地 `isAttachedToWindow` 会误报，所以用 `removeView` + `addView` 重建而不是健康检查跳过） |
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
- v3 之后白点完全依赖无障碍：无障碍被系统关掉（或应用被强行停止）后白点不会画出来，重新打开无障碍会自动补上。
- vivo 装包有个坑：**息屏状态下 `adb install -r` 会被系统判成 `INSTALL_FAILED_ABORTED: User rejected permissions`**，
  先 `adb shell input keyevent KEYCODE_WAKEUP` + `wm dismiss-keyguard` 再装即可。
- 覆盖安装时应用会被强停，装完需要重新点一次 App 里的「②」把白点拉起来（无障碍开关保持）。

## 重新编译

```
.\build.ps1
```

依赖本机 `C:\Users\xx-my1-\Android\Sdk`（platforms;android-30、build-tools;35.0.0）+ JDK 21，全程不走 Gradle（aapt2 → javac → d8 → zipalign → apksigner）。
签名用项目根目录的 `keystore.jks`（口令 android），**不要删**，删了会变成新签名导致覆盖安装失败。

代码结构：

```
src/org/xx/volumedot/MainActivity.java   界面：无障碍引导 + 尺寸/透明度滑块 + 隐藏/显示（状态每秒刷新）
src/org/xx/volumedot/DotService.java     白点：无障碍浮层窗口 + 拖拽 + 单击/长按/双击 + 位置与外观记忆 + resync/看门狗自愈
src/org/xx/volumedot/VolumePanel.java    唤起面板（nudge + FLAG_SHOW_UI）
src/org/xx/volumedot/TapService.java     无障碍：承载浮层窗口、手势补点展开、截屏识别滑块（逐应用拖拽用）
src/org/xx/volumedot/BootReceiver.java   开机自启
```

调试日志：`/sdcard/Android/data/org.xx.volumedot/files/log/trace.txt`（点击事件轨迹）。
