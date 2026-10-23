# V62 交接

当前开发版本 0.62.0，稳定画质回退 V57。实际工作目录为同级 `WorldClockSpatial` 与 `WorldClockEditor`；此仓库是同步镜像。继续修改实际工程后须同步镜像，避免分叉；不要推送未经用户授权的新版本。

**用户最新操作偏好：验证前先告诉用户具体步骤，用户操作头显/模拟器，助手只读取日志。不得自动点击、拖动或启动会切换场景的集成测试。** 2026-10-23 用户已选择 PICO 头显，V62 已安装，但尚未收到本轮视觉反馈。

本轮已修正 Shared 晨昏急变（独立 ±18° 平滑地表权重）、补夜灯局部闪烁、Shared 自转 4.5°/秒、关闭系统白色 volume base panel。Full 速度仍为 3°/秒，全部 71 项稳定版原 assets 字节一致。详情及验证边界见 [V62 说明](docs/shared-desktop-v62.md)。

103 项单元检查、编译图连续性/夜灯连线、APK 签名与资源校验通过；真实画面仍由用户验收。历史 V61 头显六轮切换、资源释放和时间尺通过；VKIMAG 为 Full 876,859 KB / Shared 46,787 KB，不是 V62 的新测量。

已向用户给出四项操作：进入 Shared，查看渐变和速度，夜面捏住约 5 秒观察灯光，再检查白底板和整个容器移动。后续先读取其回复与设备日志，不重复启动自动验证。Shared HMD tracking 不受支持；当前使用材质内置观察方向和 spatial pointer，不要恢复已弃用的 Shared HMD 查询方案。

APK `builds/WorldClock_v62_SharedTwilight_Shimmer.apk`，SHA256 `f08a92290251e8ee47621b5faa844cc18c817edbf3aa217d7a6e4f79a358a659`。本轮修改前快照 `worldclock_work/v62/baseline-20261023-182739`，V61 APK 另存。Git 仍有未提交工作，不覆盖、不重置。
