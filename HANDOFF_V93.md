# V93 · 地球观察确认版图标

用户确认R7加粗版，安装模拟器后回复“可以打包 git”，授权提交并发布。本次只修改应用品牌资源、应用显示名称及版本号；地球、世界时间、气温、经纬、距离和地震业务逻辑不变。

## 图标与产物

- 应用名：地球观察 / Earth Observer；包名保持 `com.wupengfei.worldclock`。
- 矢量主件512×512，正圆外径400，等粗内描边36，内径328；独立相切明暗曲线，左上太阳不变。
- PICO双层1024×1024 PNG、sRGB、前景在前/白色圆盘在后；SDF使用已安装PICO插件的原实现生成。应用图标仅使用图形标志，不把小字塞入启动图标。
- [Figma确认标志](https://www.figma.com/design/dcAVPtoJZ6gLaHjxSk57wj/Earth-Observer?node-id=72-89)；[应用图标](https://www.figma.com/design/dcAVPtoJZ6gLaHjxSk57wj/Earth-Observer?node-id=83-87)。
- 版本0.93.0 / versionCode93；`EarthObserver_v93_ApprovedLogo.apk`，202378635字节。
- SHA256：`a2001cca822d58f83fb5ebcec81a5fb57f8e75b3a916571ff8cc8bd57c19af4c`。
- [GitHub版本下载](https://github.com/pengfei2028-arch/world-clock-pico/releases/tag/v0.93.0)。仓库保持私有，开发签名保持原样。
- 可编辑品牌源、PNG/SDF和构建记录位于 `branding/earth-observer-r7/`；其余branding目录为历史稿，保留回退，不用于应用。

## 验证

- 离线构建成功；222项现有单元测试，0失败/错误/跳过。
- APK v2签名校验通过；5张APK图标/SDF解码像素与源导出完全一致；分层合成与平面图标像素完全一致。
- 全部74项场景assets与V92保持字节一致；实际运行工程及Git镜像206项文件一致。
- 已保留数据覆盖安装emulator-5554。用户手动启动；日志显示21:22:07开场完成、21:22:37确认系统Shared Space、21:22:45桌面资源释放。
- 本次进程日志未见FATAL EXCEPTION或Fatal signal。Shared底板配置的三次重试出现超时，已如实记录；本次品牌更新未修改该逻辑。
- 未连接头显；不将模拟器结果当作头显图标观感、佩戴手势或功耗验证。用户操作设备，未自动注入输入。

## 继续工作与回退

实际运行工程位于 `../WorldClockSpatial`，本仓库对应镜像 `WorldClockSpatial/`。当前是原生PICO Spatial SDK6.1.9 Kotlin/Compose，不新建Unity工程或切换云端。

修改前备份：`../worldclock_work/v93/pre-r7-20261025-211958/`，含两侧品牌资源、配置、Git差异和AGENTS备份。V92 APK及V90发布标签保留。用户原有未提交修改已保护；本次提交包含V91–V93最终品牌改动及相关历史稿。
