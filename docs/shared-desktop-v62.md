# V62 Shared 晨昏、夜灯与底板

2026-10-23，基于原生 Kotlin / Compose / PICO Spatial SDK 6.1.9。V61 安装包和本轮修改前的实际工程、Git 镜像快照均保留，可恢复。Full 仍以用户确认的 V57 为稳定画质基线。

## 本轮改动

- Shared 地表明暗不再使用 `sqrt(max(N·L,0))`。该函数在太阳地平线处有无界斜率。替换为连续的线性包裹明暗，再通过太阳高度约 −18° 至 +18° 的五次平滑权重过渡，端点一阶、二阶导数为零。云层沿用同一明暗权重。范围是项目画质选择，太阳位置仍来自真实/预览 UTC。
- 城市灯火与地表渐变分开：保持 Full 的 0° 至 −12° 灯火渐入，白天不亮灯。加入与 Full 相同的 100×50 UV 区域噪声和 .5–1.5 亮度变化；连续时间坐标为 `.37 + effectSeconds * .55`。不是整颗夜面统一明暗呼吸，不增加纹理采样器。
- Shared 自转 3→4.5°/秒，约 80 秒一圈。共用 `GlobeMotion` 的防抖、暂停、3 秒松手停留与回正策略。Full 仍为 3°/秒，返回时平滑收敛。展示速度不修改 UTC；捏住时效果时钟继续，夜灯仍闪烁。
- 立体容器的白色系统底板设置为 `VolumeBasePanelType.None`，既改声明默认值，也在容器挂载时更新已存在的容器参数。保留 `CaptionBarType.Default` 作为整容器摆放入口。官方 6.1.9 明确提供 [None 底板选项](https://developer.picoxr.com/spatial-api/6.1.9/spatialui/platform/com.pico.spatial.ui.platform.resize/-volume-base-panel-type/index.html) 和 [运行时更新方法](https://developer.picoxr.com/spatial-api/6.1.9/spatialui/platform/com.pico.spatial.ui.platform.containers/-window-container-params-updater/index.html)。底板隐藏后的具体操作体验仍需用户在头显确认。

## 资源及验证

桌面仍为一个不透明 Unlit 材质、IBL 系数 0、3K 日图 + 2K 夜图 + 2K 海陆/云层复合图。三张编译纹理含 mip 共 47,536,449 字节，预算与 V61 相同；本轮没有声称重新测量 GPU 内存或功耗。

已完成：

- APK 构建及签名校验，版本 0.62.0 / 62。
- 103 项单元检查全部通过，包括 Shared 加速、Full 速度恢复、UTC 不变、暂停/停留期间效果时钟继续。
- 对实际编译图求值：地表明暗单调，晨昏两端和地平线处斜率连续，地平线上方夜灯权重为零；确认夜灯噪声和运行时时间参数已连接。
- 最终 APK 内的 DesktopEarth.bundle 与本轮导出一致；全部 71 项稳定 V57 原 assets 字节一致。
- SWAN / B3110 覆盖安装成功，设备确认 0.62.0。用户选择在头显手动验证。

等待用户操作确认：晨昏观感、4.5°/秒速度、夜灯局部闪烁、白色底板消失及整容器可移动。代码和编译图检查不替代真机画面验收。本轮不自动操作模拟器或头显，不运行会切换场景或注入手势的集成脚本；先给出步骤，由用户操作，助手读取日志。

V61 历史真机六轮结果：Spatial server `VKIMAG` 从 Full 876,859 KB 降到 Shared 46,787 KB，并在各轮保持一致。这是图像资源计数，不是总 GPU 内存或功耗；不冒充本轮 V62 测量。

## 文件与回退

- 运行实现：`WorldClockSpatial/app/src/main/java/com/wupengfei/worldclock/content/DesktopEarth.kt`、`GlobeMotion.kt`、`Main.kt`。
- 生成器：`tools/generate_desktop_earth.py`；独立模型/场景为 `DesktopEarthV62.usda`，V61 源保留。
- 构建后执行 `tools/verify_desktop_asset.py`，检查编译包、APK 与 V57 基线。
- 本机 APK：`builds/WorldClock_v62_SharedTwilight_Shimmer.apk`，SHA256 `f08a92290251e8ee47621b5faa844cc18c817edbf3aa217d7a6e4f79a358a659`。
- 本机完整回退快照：`worldclock_work/v62/baseline-20261023-182739`，包括同步前两份工程、生成器、材质和资源校验。
- 本轮仅本地修改和安装，尚未推送 GitHub。
