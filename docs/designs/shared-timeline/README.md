# 共用时间滑条 · Figma 已确认

用户已确认布局，并在 Figma 修改温度计开启色和温标端部圆角。2026-10-22 读取结果：开启色 #745BFF、82% alpha；色条高 10 dp、端部半径 5 dp。

参考：https://www.figma.com/design/dcAVPtoJZ6gLaHjxSk57wj/Untitled?node-id=2-34

实现原则：沿用工程 PICO Material.Regular / backgroundMaterial、SpatialHoverStyle.Highlight、PicoTheme 与系统字体；Figma 背景模糊和阴影不移植。20×20 温度计矢量转为 Android VectorDrawable，路径与描边保留；48×48 官方材质圆按钮，开启色是项目选择。

滑条宽 528 dp，整行宽 720 dp；左侧 +，右侧温度计、更多，间隔 16 dp。时间尺、caption 与温标共享中心；空间锚点补偿新按钮增加的宽度和温标预留高度，切换模式不移动 UI。600 ms 五次平滑渐变由场景帧更新，共用于球壳和温标。

V57 实现与验证结果见 ../../global-temperature-v57.md。原 V56 与修改前源码在本机保留；此次不修改 Figma 原稿。
