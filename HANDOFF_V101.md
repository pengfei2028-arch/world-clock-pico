# V101 — 系统照片选择器独立平面窗口

2026-10-26，版本 0.101.0 / 101。用户报告 Full Space 添加照片仍出现在脚下；头显 PB3310PGL7200028B 连接并停留在故障界面后完成只读取证。没有接管 Windows 桌面，没有头显输入注入，没有提交或推送 Git。

## 现场证据与修复

- V100 头显日志：PickMultipleVisualMedia 解析为系统 `com.android.providers.media.module/...PhotoPickerActivity`。它与 `LaunchActivity` 同处 task 82 / container 57；窗口尺寸 2560×1440。V97 的 source bounds `[0,0][1248,408]` 仅用于 Android 展开动画，不能确定外部窗口的三维位置。
- 删除 Stage 内直接启动选择器及 scale-up 动画。注册 `Form.Planar` 的 `FootprintPhotoPickerV101`，由独立 `FootprintPickerActivity : SpatialStubActivity` 承载，初始大小 800×600 dp，官方默认窗口摆放和 Caption Bar。
- 等平面内容挂载、Activity 恢复并取得窗口焦点后，仅启动一次系统选择器。仍使用系统 PickMultipleVisualMedia，不新增图库权限，不扫描整个设备图库，不另做照片浏览系统。
- 选完/取消只关闭选图容器，保留沉浸 Stage。捕获城市 ID，结果回到同一请求；保存城市与 launched/completed 状态，防重复启动，销毁取消未完成请求。初始化本地相册逻辑、导入上限、城市标记挂载修复保留。
- 使用当前 SDK 6.1.9 的公开 DSL、Activity 和 Context 容器接口；没有隐藏 API、手写外部窗口世界坐标或新资源。

## 验证与交付

- 离线 assembleDebug/testDebugUnitTest 成功，251 项通过。
- 74 项 APK assets、379 项 res 与 V100 字节相同；222 个运行时工程文件与 Git 镜像一致；APK v2 签名有效。
- 后台无窗口模拟器：系统选图在正前方可见，task 14 内包含 PickerActivity 与系统 PhotoPickerActivity，地球 Stage 保持独立。连续 3 次取消/重开后没有遗留两个选择器 Activity，原相册恢复；导入一张既有模拟器地球截图到纽约，保存 1 / 失败 0，地球圆形缩略图立即出现。
- 没有测试或声称真实手势、头显窗口高度已验收。V101 已覆盖安装 PB3310PGL7200028B，设备报告 versionCode 101 / versionName 0.101.0；已请用户手动打开并确认正前方位置、取消重开及选图返回。等待反馈。
- 当前照片数据没有清理；未改变 Dock 输入、地球资产、天气、地震或经纬/距离逻辑。

APK：`C:\Users\Admin\Documents\Codex\2026-10-20\new-chat-2\builds\EarthObserver_v101_PhotoPickerWindow.apk`

SHA-256：`539e0e1e15d52564561ab4c2afee46d5e17749530f2496323f817da68334108f`

大小：202725767 字节。

## 恢复与后续

- 实际工程 `..\WorldClockSpatial`；Git 镜像 `WorldClockGitHub\WorldClockSpatial`。Git HEAD 仍为已发布 V93，V94–V101 未提交工作保留。
- 修改前完整快照：`..\worldclock_work\v101\pre-picker-position-20261026-232559`，234 文件、Git 原始差异及状态；`baseline-files.json` / `backup.json` 可核对。
- 证据：`..\worldclock_work\v101\headset-picker-windows.txt`、`headset-activities.txt`、`headset-picker.log`；模拟器 `v101-picker.png`、`v101-imported.png` 及相应日志/任务转储。`headset-picker.png` 是失败的 50 字节 screencap 输出，不可当截图。
- SDK 本地官方参考：`spatial-sdk_spatial-container_customize-the-activity-of-spatial-containers.md`、`spatial-design_space-status.md`、`spatial-design_foundation_window.md`。Full Space 支持当前应用的 WindowContainer；实际物理位置最终由系统决定，不能仅凭默认摆放说明替代头显验收。
- 后续只在后台读取头显日志，用户手动操作。若仍偏低，先捕获 V101 PickerActivity 所在任务/容器和系统空间状态，不恢复 Stage 直接启动的旧路径，也不再把 2D 动画参数当成三维位置修复。
