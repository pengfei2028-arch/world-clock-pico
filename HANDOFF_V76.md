# V76 · 地震颜色与波纹轮播（2026-10-25）

当前工程：WorldClockSpatial，Kotlin / Compose / PICO Spatial SDK 6.1.9。
Git 镜像：WorldClockGitHub/WorldClockSpatial。用户手动操作设备；不自动启动、点击或拖动。

## 本次修改
- M<5 白点；5≤M<7 黄点 (#FFD54F)；M≥7 红点及红波纹 (#FF5145)。M5–7 波纹仍为弱白色；选中不覆盖震级颜色，以波纹范围及强度区分。
- 继续 V75 的共享透明材质、贴球面局部贴片和固定 3 个批次（点/波纹/数字）；地震材质没有贴图采样器和波纹图片。数字共用 528×64 系统字体图集，详情保留一个官方 UI 面板。
- 最多 6 组、每组 3 环，单环 2.4 秒，错开 0.8 秒；每 9.6 秒轮换。选中事件持续；最多两个 M7+ 优先位、最多两个回放刚出现事件优先位，其余按最久未播放、震级、发生时间轮换。
- 候选是所选 24h/7d/30d 周期与最低震级筛选后的可见聚合代表。波纹中心保持代表事件的真实坐标；普通相邻波纹至少 8° 间隔，选中周围 10°。密集区域可能少于 6 组。
- 回放仍只允许发生时间≤回放时间的事件出现；拖回过去立即丢弃已经不在可见事件集合的波纹。暂停回放冻结历史时间，波纹展示继续。后台/关闭图层使用既有生命周期。
- 移除只用于旧版逐环模型的 QuakeWaveGeometry；坐标测试改为验证实际球面贴片。
- 修复重复 8K 云图句柄：导入 mask 解绑并释放后，再加载原 RAW 云图；原图、地表及其他资产字节不变。

## 资源核对
Full 实际显示使用 5 类 8192×4096 原图：AI 昼图、夜灯、法线、AI 海陆高光、云层。
APK 还包含历史资源/导入包中的被替换纹理，不等于它们全部同时参与渲染。V74 释放了旧 normal/specular；V76 释放旧 cloud mask。
Shared 仍为独立 3K+2K+2K，不创建地震或经纬图层。
没有新增/遗留逐事件地震 UI 图片资产。旧运行路径已移除；V73–V75 APK、源码快照与设计资料作为回退保留。

8192×4096 可无降采样拆为两张 4096×4096，但总像素不变、不能单靠拆分省显存。两张 4096×2048 只剩一半像素。
若后续授权拆分，优先保留原连续网格和法线/切线，处理纹理跨边采样及每级 mip 边缘；需 Blender 与目标 SDK 分别验收。当前未拆分或修改原 Blender。

## 验证
- testDebugUnitTest + assembleDebug：159 项通过，0 失败；包括阈值、6 槽元数据、轮换公平、M7 优先、选中保持、日期线/极区邻近抑制、回放未来事件排除、重置。
- 编译后的 MaterialX 求值：6 槽独立、白黄红分级、红波纹、循环连续、选中/出现遮罩通过。材质 bundle 69,716 字节，0 贴图。
- 73 项非地震原 assets 不变；只更新 QuakeDecals.bundle。实际工程的 12 个变更文件已同步 Git 镜像并核对修改前一致性。
- APK v2 签名通过，覆盖安装 emulator-5554。
- 用户手动启动 PID 25235：日志确认旧云图 count=1/remainingValid=0，地震材质加载；截图确认白/黄/红点及红色波纹，原地球和云层正常显示。
- 7 天周期 239 个筛选事件实际运行；日志显示 6 组波纹 ID 随轮换变化，固定批次 3，稳定时 meshHandles=3；手动转动时旧网格短暂退休可升至 4。截取日志未见 FATAL/NO_MEMORY/Batch failed。
- 不把句柄释放当作 GPU 内存实测；未连接头显，无功耗、GPU 峰值、完整多次往返或佩戴视觉结论。已请用户手动核对 30 天/选择持续播放，反馈待续。

## 产物与回退
APK：builds/WorldClock_v76_QuakeColorsAndRotation.apk
SHA256：cbf45ca5f656e4474aa87020618f441d9ea7c21b86fa965dad3a18b845ce78a5
文件大小：217550218 字节。
备份：worldclock_work/v76/backup-path.txt 指向含实际工程、Git 镜像、材质源的完整修改前快照；V75 APK 保留。
证据：worldclock_work/v76/build.log、runtime-25235.log、runtime.png、bundle-inventory.json、baseline-assets.json、changed-files.json。
生成：tools/generate_quake_material.py → Sources/Models 与 Scenes/QuakeDecalsV76.usda → QuakeDecals.bundle。
校验：tools/verify_quake_material.py。
本轮未推送 GitHub；V73–V76 修改仍在既有本地分支，不能覆盖或丢弃。
