# V57 · 共用时间滑条与官方 UI 材质

状态：V57 / 0.57.0 构建成功，98 项单元测试通过。2026-10-22 用户反馈模拟器体验正常，并确认本版稳定、授权上传 GitHub。第一次自动集成运行因测试用例线程错误中断；测试源码已修正，尚无重跑通过记录。手动体验通过与自动验收状态分别记录；未声称 V57 真机性能或视觉已验收。

## 用户已确认的设计

Figma 只作布局、图标和选中颜色参考；继续使用当前 PICO Spatial SDK 6.1.9 的 Material.Regular / backgroundMaterial、SpatialHoverStyle.Highlight、PicoTheme 及系统字体。没有迁入 Figma 模糊或阴影。

用户修改后的温度计开启色为 #745BFF、82% alpha。48 dp 圆按钮内使用 20 dp 白色温度计，来自 Figma 的三个矢量路径完整转为 Android VectorDrawable。温标高 10 dp，两端半径 5 dp。这些尺寸、颜色与动画时长为项目选择。

## 工程改动

- 删除独立文字模式面板和 TemperatureRuler。温度计位于既有时间尺右侧，更多按钮顺延。
- 两种模式都使用同一 TimeRulerDrawing 和拖动入口，每 528 dp 对应 360 分钟。固定六小时视口，超出当前视口时平移；气温选择钳制在 manifest 的真实数据时段，小时选择取最近真实帧，不伪造中间观测。
- 温标与时间尺左右边界对齐。固定预留温标位置，并补偿空间面板锚点，切换模式不改变原时间尺和 caption 的位置。
- 两种模式分别保留时间与视口；真实现在标记不跟预览时间改写。保持原暂停、释放等待和回正入口。
- 温标与独立气温球壳共用 600 ms 五次平滑渐变，可在中途反向；首次等待真实帧就绪再淡入。退出时原地表高光随覆盖强度恢复。
- 未修改地球 8K 底图、气温配色、真实数据、太阳、地球旋转、云层策略及城市资源。

## 已完成检查

- 用户手动验收：先反馈“模拟器没啥问题”，随后确认“挺好挺稳定的”并要求上传此版。没有将这项反馈写成自动集成通过或真机测量结果。
- 上传前核对：实际运行工程与 Git 副本的 147 个运行时源码、测试、资源及 app 构建配置文件 SHA256 完全一致；APK SHA256 与构建时记录一致。
- 运行时全部 71 项 assets 与修改前 Git 副本逐字节 SHA256 一致。
- 温度计尺寸及三个路径与 Figma 导出文件一致。
- Git diff --check 通过，实际工程与 Git 副本的改动同步。
- 18 个测试套件、98 项单元测试通过，失败/错误/跳过均为 0；其中新增 6 项覆盖小时增益、边界、时间恢复、无数据和可逆渐变。
- V56 与实际生成的 V57 APK 内 71 项 assets 全部逐字节 SHA256 一致。
- 安装包元数据确认 versionCode=57、versionName=0.57.0、applicationId=com.wupengfei.worldclock；测试 APK 也已编译成功。
- 第一次模拟器运行确认 V57 启动、气温帧提交；SharedTimelineIntegrationTest 在 instrumentation 线程读取 shellOpacity 时抛出 IllegalStateException，1 项运行 / 1 项失败。不是通过记录，不能据此判断动画或 UI 验收通过。
- 测试中的透明度和原地表检查现统一经 runOnMainSync 读取；等待谓词也在主线程执行。渐变采样覆盖数据准备阶段，完成状态采用有超时的轮询。只修改测试及本机验证入口，应用代码、APK 和素材未因本次修正改变。修正后的用例尚未编译运行。
- Android UiAutomation 截图为全黑，不能代表 PICO XR 合成画面。后续同类文件加 -android 后缀，仅作诊断。验证入口在已安装 pico-cli 可用时尝试官方 capture screenshot --device emulator-5554 --out；未安装或失败会记录待视觉检查，不下载工具、不改 SDK。运行结束后的截图不能代替各切换阶段或真机验收。

## 构建阻塞及待验收

当前会话的执行权限令 Java Path.toRealPath 对现有 Gradle JAR 及新建工作区文件均抛出 AccessDeniedException。已尝试只读依赖缓存、复制 Gradle 和缓存到可写工作区；仍在 Kotlin 编译前失败。请求扩大构建权限被当前自动审批策略拒绝。没有更改权限、绕过限制或将旧 APK 标为新版。

2026-10-22 用户手动执行 tools/build-local-v57.cmd 后，日志显示 BUILD SUCCESSFUL in 29s，98 项测试通过。此结果确认普通本机构建环境可以编译工程；当前会话的 Java/ADB 执行限制仍未解除。

重新执行同一个 tools/verify-local-v57.cmd 即可：入口先离线重编 Android 测试包，再安装现有 V57 应用与新测试包并运行。编译失败即停止安装；仅以 emulator-5554 为目标，未连接时退出。结果写入 builds/v57-verification，首次失败日志及旧测试源码已保留在 worldclock_work/v57/verification-attempt-1。默认/开启/恢复画面和实际按钮交互仍需检查。

截图命令依据：[PICO CLI 官方命令参考](https://developer.picoxr.com/document/pico-cli/command-reference/)。当前会话受执行权限限制，未自动调用该命令或新的本机验证入口。

安装包：builds/WorldClock_v57_SharedTimeline.apk，202601665 字节。

SHA256：7108dde7b910d527421a44d76e9a71479ee1fe2ffb3d048c12efbe450a3419cf。

V56 Git 基线为 c0b31833ae5998303cdfd668bc0dc306d2084d99。修改前实际源码与测试备份保存在本机 worldclock_work/v57/baseline，V56 APK 保留。
