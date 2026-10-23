# V61 Shared 地球：高光、云层与拨动

基于原有 Kotlin / Compose / PICO Spatial SDK 6.1.9。稳定回退仍是用户确认的 V57；本版为本地体验版。Full 的原始资源不变，没有新工程或云端迁移。

## 操作与外观

- Full 更多菜单选择“收起到桌面”，真正关闭 Stage 并核实 `SHARED_SPACE`。
- 用系统底座移动整个容器；用户已确认地球和操作按钮一起移动。拨动地球只改变姿态，两种操作独立。
- 底部官方滑条调节直径 18–35 cm，默认 25 cm；旁边 32 dp 圆按钮使用官方 `ic_sui_double_arrow_right` 展开。控件位于独立前置平面，最大地球不覆盖它。
- Shared 使用原 Full 的 `GlobePointerInput` 和 `GlobeMotion.tick`，保留 336°/m 增益、防抖、70°/s 上限、松手停留 3 秒及至少 4 秒回正；3°/s 展示自转与 UTC 分离。
- 桌面表面仍为不透明 Unlit，所有实体的环境 IBL 系数为 0，没有实时灯。原颜色贴图显式 sRGB 解码；UTC 太阳方向控制日夜，0° 至 −12° 晨昏沿用共同策略。
- 高光使用原用户海陆高光图的 2K 副本，根据表面法线、太阳和材质内置观察向量计算。海洋反射更集中、强度更大，陆地更柔和。这是轻量材质模拟太阳反射，并非第二个实时太阳灯，也不是与 Full PBR 完全等价的材质。
- 云图来自原静态 Blender 云图，降为 2048×1024。云层跟随同一地理旋转，交互时 .45 秒淡出、恢复时 1.4 秒淡入。通过地表 RGB 合成，避免额外透明球壳及过度绘制；不具备独立云层高度、体积或实时气象数据。

## “透明”的排查结论

原地球 opacity=1、OPAQUE；去掉根实体透明控制器及换成 SDK 纯黑不透明材质，靠近沙发时仍可见背景。用户随后明确确认：**将整个容器移到面前空处后不透明了**。结合对照测试，现象与系统空间遮挡/靠近真实环境的位置处理有关，不能归咎于 IBL 或靠提高亮度掩盖。

本版保留系统真实环境遮挡，没有强制置顶或绕过系统安全显示。正常摆放在物体前方的空处；不承诺进入真实物体内部时仍覆盖背景。尚未在头显上确认系统采用的具体遮挡阈值。

SDK 6.1.9 官方说明：[Unlit 不透明模式](https://developer.picoxr.com/spatial-api/6.1.9/spatialpack/core/com.pico.spatial.core.ecs.resource/-unlit-material/set-opacity.html)。V60 的固定姿态、固定 UTC 配对截图中，IBL 0→64 和强红色实时灯没有改变球体内部像素；该测试隔离外部光照，不用于证明系统环境遮挡已关闭。

## 资源和生命周期

| 桌面资源 | 分辨率 | 用途 |
| --- | --- | --- |
| desktop_day_3k.png | 3072×1536 | 日间颜色 |
| desktop_night_2k.png | 2048×1024 | 夜灯 |
| desktop_surface_2k.png | 2048×1024 | R 海陆高光、G 云遮罩，RAW |

只有 3 个纹理采样器和一个不透明地表材质。包含 mip 的编译纹理数据合计 47,536,449 字节，约 45.33 MiB（含少量格式头）。这是包内纹理预算，不是测得的 GPU 内存或功耗。Shared 不加载原始 8K 法线、高光、云图或完整场景。

桌面显式关闭模型、材质、三张纹理、网格、碰撞资源、输入和帧订阅。Full 新增对导入材质、网格和五个导入纹理句柄的显式释放，避免仅依赖 `Entity.destroy` / `AssetBundle.close` 与稍后的 GC。不修改 Full 的渲染参数、纹理文件、网格或业务交互。

官方 HMD tracking 文档明确限定 Full Space，因此 Shared 不创建 HMDTrackingProvider。反射改用官方 `ND__viewdirection_vector3` 材质节点；手势使用 `detectSpatialPointerEvent`，将其 view-local 位置转换到容器中的静止原点，结合 `LocalVolumeViewPointManager` 确定前/侧/后观察方向，再复用原输入与运动策略。不能通过不受支持的 HMD 查询来模拟 Shared 视角。

调试中曾出现 SDK `NO_MEMORY`（170.67 MB 请求、仅余 84.07 MB）及随后初次加载也失败；保存日志后重启模拟器，在干净图形服务中复测。不能把重启后短测通过解释为长期泄漏已排除。

## 验证

已在 PICO Emulator 6.1.0 和本轮后接入的 SWAN / B3110 运行。自动化真机测试不等同于佩戴观感、眼动和手部跟踪验收。

- 最终 APK 与稳定 V57 对比：全部 71 项原 assets 字节一致。
- 材质编译契约检查：模型 V61、3 个采样器、观察/太阳/云密度参数、opacity=1、tone mapping 关闭、APK 内 bundle 与实际导出一致。
- 单元测试：102 项通过。
- `DesktopSurfaceTest` 通过：真实 Shared/Full 状态、碰撞和交互组件、注入手部轨迹后的旋转/暂停/停留、云层淡出淡入、太阳参数一致、返回 Full 后桌面原生资源句柄全部失效。
- 已取得昼面和夜面 XR 合成画面，云图可见、球体不透出后方墙饰、最大尺寸下底部控件未重叠。历史截图名 DAY_CLOUDS 对应 UTC 19Z，NIGHT_CLOUDS 对应 UTC 07Z；名字并不代表当前朝向是昼面或夜面，以画面为准。
- 最终材质高光 A/B：相同 UTC、姿态及云密度，关闭海陆高光后仅球体区域 13,344 像素变化，最大单通道差 153/255；海面亮斑可见。初版 HMD 方案的配对截图完全相同，因此已弃用，不算通过证据。
- 模拟器六轮 Shared/Full、重复点击、拒绝展开后的恢复、气温状态与关闭语义通过；时间尺测试去掉固定 180ms 帧时序假设后通过，未更改正式渐变逻辑。
- 模拟器后四轮 Shared PSS 445.6–453.5 MiB，Full PSS 527.2–532.4 MiB；Shared native allocated 52.9→53.7 MiB，Full 55.1→55.7 MiB。PSS 不是 GPU 内存，也不用于宣称零增长。每轮 Full 导入的 11 个资源句柄关闭后均失效。
- SWAN `DesktopSurfaceTest` 通过（33.403 秒）：真实双向空间状态、原画质恢复、注入拖动/停留、云层和桌面资源释放。

模拟器若失去前台，曾降为约 1Hz，使按帧推进且限幅的开场动画超时；恢复前台后原测试通过。另有历史 XR 截屏局部黑块/截断，验证截图需重拍完整帧。两者不据此修改稳定 Full 的动画或资源。

注入轨迹验证逻辑及渲染接入，不替代真机手势、眼动命中或系统容器移动验收。功耗、真实 GPU 内存下降、长时间稳定性、转头走动的佩戴体验仍待头显验证。

## 重建和回退

1. 运行 `tools/generate_desktop_earth.py --editor <实际 WorldClockEditor 路径>`；`--shader-only` 只重建材质、复用已派生贴图。
2. 使用 PICO Editor 打包 `Sources/Scenes/DesktopEarthV61.usda`。为 `pico-cli editor pack` 提供绝对 `--project`、`--target-app-root` 和 `--output-path`，输出到运行工程 `app/src/main/assets/DesktopEarth.bundle`。
3. 构建 APK 后运行 `tools/verify_desktop_asset.py --bundle <bundle> --apk <apk> --baseline-apk <V57 apk>`。这一步防止误用编辑器 Build 目录中的新包、却在 APK 中继续带旧包。
4. 原 8K 源文件始终保留；V60 工作文件快照在本机 `worldclock_work/v61/baseline`，V57 稳定 APK 和远端 release 分支保留。

没有自动桌面吸附、锚点、跨重启物理位置保证或软件关闭后继续显示。其他应用进入 Full 时共享容器显隐交给平台；本版按可见性暂停，恢复时不追赶隐藏期间的展示转角。
