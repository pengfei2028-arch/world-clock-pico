# V100 — 恢复时间条输入，隔离只读提示

## 用户要求与工作方式

用户报告 V99 后时间条 hover 不稳定、交互失效，并感到帧率下降，要求模拟器验证；随后明确要求不要抢占电脑。后续默认后台构建、ADB、日志与后台模拟器，禁止激活窗口、移动宿主鼠标、发送宿主键盘输入。连接的头显 PB3310PGL7200028B 本轮未操作。不要把后台自动验证推广为佩戴验收。

## 修复

- V99 把关闭空间命中的 provider 放在整个 Dock 父层；SDK 同分支的 provider 覆盖规则使实际控件受父层影响。这是本次输入回归的明确代码风险，撤销该结构。
- Dock 恢复 V98 原生玻璃、hover、时间尺/Caption 手势和普通透明过渡；从 912×222dp 改为 912×130dp，去掉顶部 92dp 透明留白。同步改附件锚点，按钮、时间尺、Caption 的世界位置不变。
- TimeStatusPanel 为独立 528×80dp 只读附件，完全不包含按钮；仅这个独立附件使用 zOffset + enableSpatialHittestProvider(false)。距离提示、地震周期文字、气温图例/状态、时间提示放这里。
- 回到现在、气温“现在”、地震“返回总览”是另外一个普通可交互附件，避免只读设置影响按钮。仍随原 Dock 位置更新。
- 删除“点选地球上的照片查看相册”。移除 V99 SpatialInput.kt 的自定义 saveLayer 淡出和广泛命中改动；城市标签、经纬和距离卡恢复 V98。**这意味着不能宣称城市/经纬/公里读数等所有被动区域均已实现穿透**；本次先修复已报告的 Dock 回归。
- 不改足迹数据、V98 照片标记生命周期修复、已确认图标或地球资源。

## 验证与限制

- 离线 assembleDebug、251 项既有 JVM 测试通过；0 失败/错误/跳过。首次测试出现 Windows 临时目录重命名文件占用，未改代码重跑及最终交付构建均通过。
- 全部 74 项 APK assets、379 项 res 与 V99 字节一致，无新增贴图或运行依赖。221 项源码/资源/构建文件与 Git 镜像一致，v2 签名通过。
- 使用 Pico_Spatial_6_1 AVD 的后台实例，ADB emulator-5554。运行前将 app files/shared_prefs 备份到 `../worldclock_work/v100/sim-user-data-before.tar`，没有清空数据。完成启动和录屏观察，地球、云层、昼夜与 Dock 可见。
- 后台按已查明的 PICO Android 窗口层 ID 输入：连续三次左右拖时间条，每次独立“回到现在”按钮均消除预览；时钟/足迹/距离/地震入口、菜单开关正常；Caption 向右拖后反向拖回，日志位置 29.75dp → 0.10dp。截图确认距模式提示与地震周期文字正确显示，足迹旧提示不存在。
- **这些是指定窗口层的输入检查，不等于真实空间射线命中、gaze/hand hover 或提示区域射线穿透验证。** 后者仍须用户在可见模拟器/头显手动确认。
- 后台测试有平台限制：XR eye gRPC 输入使无窗口模拟器宿主退出；重新后台启动后曾记录 com.pico.spatial.runtime 的 SIGSEGV，系统连带停止 app，原因尚未定论。恢复后使用窗口层检查通过并运行数分钟；不据此宣称模拟器完全稳定。不要重复使用无窗口 XR eye 注入。
- 后续稳定区间动画回调约 59–60Hz，CPU 回调均值约 1.8–2.2ms；构建竞争期间存在大间隔。**回调不是显示 FPS**。Android gfxinfo/graphicsstats 对本 app 返回零帧，无法据此证明 GPU 帧率提升或无掉帧。已移除 V99 额外离屏混合，实际性能仍待同场景手动确认。
- V97 外部选图窗口高度、V98 新照片即时标记保持原待验收记录。

## 交付

- versionCode 100 / versionName 0.100.0，包名 com.wupengfei.worldclock。
- APK：`../builds/EarthObserver_v100_DockInputFix.apk`
- 大小：204136618 字节。
- SHA256：`b439761e81ad052bff5caca490c85c5cce8273e59cb02114ff86e265598dba83`。
- 最终 APK 已保留数据覆盖安装后台模拟器；未推头显。
- 最终包再次启动日志确认入场完成；随后核对实例 PID/AVD/无窗口参数，已正常关闭本轮后台模拟器释放电脑资源，安装与用户数据保留在该 AVD。
- 备份：`../worldclock_work/v100/pre-v99-regression-20261026-223530/`，234 项工程文件及此前 Git 未提交/未跟踪工作。没有 commit/push，远端仍为原发布状态。
- 构建、校验、录屏与输入记录：`../worldclock_work/v100/` 下 build-delivery.log、package-verification.json、ui-check-results.json、v100-layer-drag.png、v100-distance-hint.png、v100-quake-menu.png、v100-ui-runtime.log。

## 后续手动重点

时间条悬停应稳定并能反复拖动；预览后的返回按钮仍能点击；足迹旧提示区域应可拨地球；距离/地震文字区域的空间射线穿透尚需确认。保留正式控件阻止点击穿透的行为。用户未允许接管电脑，后续继续后台处理，需要手动操作时再简短告知用户。
