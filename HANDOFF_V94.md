# V94 · 关闭后重新入场

用户报告 Full 的 Caption Bar 关闭后再打开跳过入场，Shared 关闭后重开正常。本轮修复生命周期，不调整画质、图标或其他功能。

## 原因与修改

- 原 `WorldClockSession` 是进程级单例，`GlobeEntrance` 播放完成后保留状态。原 `onLaunch()` 只恢复渲染开关，没有重建入场状态；Android 移除任务后可能缓存进程，因此后续打开会复用已完成的入场。仅凭没有入场动画不能断言关闭后仍在持续绘制。
- 新增 `WorldClockRun/WorldClockRuns`，在无 Stage、无共享容器、无切换、无渲染资源的启动边界创建新 ClockState、GlobeMotion、GlobeEntrance 和地理基准。兼容新 Activity 与 singleTask 的新 Intent。
- 活跃 Full / Shared 及切换中的启动保留同一状态；收起、展开不重复入场。新一轮打开从已保存偏好重新加载城市。
- 关闭先收起面板、暂停地震回放并移除两种场景内容，等待资源释放（最长3秒），再关闭容器和本应用任务；资源的已有销毁路径取消帧订阅、追踪及 Compose 任务，释放原生句柄。超时会记录实际状态并继续结束任务，不伪报释放成功，不强杀进程。
- HomeStage 的 Compose key 增加本轮 ClockState 身份，避免新一轮复用旧场景。

## 产物与检查

- `../builds/EarthObserver_v94_CloseReopenFix.apk`，0.94.0 / versionCode94，202395019字节。
- SHA256：`8293085bae66b06ce22074063e8fe1373a3071eb8004a32b75dcdd87aff0d4ff`。
- `testDebugUnitTest assembleDebug --offline` 成功，225项单元测试，0失败/错误/跳过。新增3项覆盖同进程重开、连续20次重开、Full/Shared/切换与资源存活时保留状态。这些不是设备生命周期验证。
- APK v2签名通过；全部74项场景资产、21项源资源及APK全部 assets/res 条目与V93字节一致；208项源码/资源/构建配置与Git镜像一致。
- 保留数据安装到 `emulator-5554`，安装后包管理器确认0.94.0，更新时间2026-10-26 15:11:27。安装后未自动打开应用，未注入输入。当前未连接头显。
- 待用户手动验证：Full Caption关闭→重开两次，每次重新入场；收起→展开保持连续；Shared关闭→重开正常；原保存城市仍在。关闭资源释放和重新入场的设备日志尚待该操作产生，不能宣称已通过运行时或头显验证。

## 回退与后续

- 修改前快照：`../worldclock_work/v94/pre-close-fix/`；所有206项原文件SHA基线在该工作目录的 `baseline-files.json`。原工作区Git干净，未覆盖其他修改。
- 构建、签名、文件核对记录仅保存在 `../worldclock_work/v94/`，不提交设备原始日志或本机缓存。
- V93 APK、已发布 `v0.93.0` 和原图标保留。V94尚未提交、推送或发布，未获本轮GitHub上传指令。
- 诊断日志：`WorldClockLifecycle` 的 `V94 fresh run=... entrance=0.0`、`V94 close renderers released=... heavy=... light=...`，以及原 `WorldClockScene` 的资源释放/开场记录。仍需区分进程缓存、活跃任务与实际帧更新。

## 2026-10-26 头显安装补充

按用户要求将同一V94 APK保留数据覆盖安装到PICO B3110头显PB3310PGL7200028B，包管理器确认versionCode94 / 0.94.0、lastUpdateTime为2026-10-26 15:43:51。未自动打开或注入输入；Full关闭重开及Shared往返的佩戴验证仍待用户操作。安装回执保存在本地worldclock_work/v94/headset-install-20261026.json。

## 用户头显验收

2026-10-26，用户回复：头显验证过了，没问题。用户接受V94关闭重开修复。随后要求关闭按钮增大50%，本轮先做Figma尺寸评审，未修改应用代码或重新打包。