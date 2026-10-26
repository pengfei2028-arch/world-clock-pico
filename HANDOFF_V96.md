# V96 · 个人足迹首版（待设备验证）

用户已回复“UI可以了，开始我们的下一步吧”，授权从 Figma 进入工程；本轮又明确选择“先完成 APK，之后再验证”。因此已完成工程、测试与本地安装包，未安装、启动模拟器或头显，未推送 GitHub。V94 关闭重开修复、V95 放大关闭按钮继续保留。

## 使用

1. Full Space 中点击时间滑条左侧的脚印按钮。
2. 在“足迹地点”中搜索并添加城市。足迹地点独立保存，与世界时间收藏列表无关。之后可从时间条上方的“添加地点”再次打开。
3. 点击地点行或地球上的圆形照片标记，地球转向该地点，右侧展开对应相册。
4. “添加照片”调用系统照片选择器；系统不提供照片选择器时，AndroidX 使用系统文件选择器。用户取消则保留原相册。无可用系统入口时显示重试说明。
5. 点击缩略图切换大图；大图完整适配，圆形标记显示所选照片，其他地点显示各自首张照片。没有照片的地点使用加号占位，不放入示例照片。
6. 关闭相册、拨动地球或切换其他功能时收起面板；退出足迹保留本地相册。Shared 中不创建足迹图层。

## 工程

- `FootprintStore.kt`：独立地点/照片索引、原子落盘、内容哈希去重、路径验证。使用应用 `noBackupFilesDir/footprints`，不随 Android 自动云备份上传，不读取世界时间 `cityIds`。
- `FootprintImages.kt`：用户授权的内容 URI 逐个导入。保留原文件并生成最大边 1600 的预览、384 的缩略图；在后台按目标尺寸解码、应用 EXIF 朝向；缩略图共享缓存上限 8 MiB。拍摄日期只读 EXIF，有缺失就显示暂无。原始地球资源不改。
- `FootprintState.kt`：独立选择、空态、导入中、取消与失败、重试；选图时固定所属城市，切换城市不能把待导入照片归错地方。切换后立即撤下旧图。
- `FootprintPanels.kt`：共用 Dock 新增 48dp 脚印按钮，图标为批准的 Figma 98:78 原 SVG 转出的 96×96 PNG，以 24dp 显示。624dp 右侧面板、576×360 大图、184×108 缩略图，沿用 PICO `Glass` / `Material.Regular` / 系统字体。圆形照片自身裁切，边框画在内部，外层不裁切。
- `FootprintRenderer.kt`：复用实际模型 UV 经纬映射和显式 TRS；绝对局部坐标聚焦沿用 V82 修正后的方式。标记随地球移动并按地平线隐藏，文字朝向用户。照片槽、相册与地点面板按需创建并复用；无逐照片 3D 网格或大纹理资产。相册可读期间延长原有停留；关闭后恢复原动作规则。
- `LaunchActivity.kt`：现有 FragmentActivity 继承链中注册 Activity Result；系统选图期间保留任务；重建时保存选图目标。接回相册不应被软件关闭路径清除。这个生命周期组合仍需 PICO 实机验证。
- 共用 Dock 从 848dp 到 912dp，左侧增加按钮；补偿空间锚点后保持原时间滑条、右侧按钮及 Caption 的位置。时钟/地震左侧入口及弹层相应向左移动。所有尺寸为项目设计，不冒充官方标准。

## 验证与产物

- `../builds/EarthObserver_v96_PersonalFootprints.apk`
- versionCode **96**，versionName **0.96.0**，应用名“地球观察”，包名不变。
- 202660187 字节；SHA256 `69a57e74d8ce8c79c154b5a248e5cfac8eac62e4ac664d5e182ea5eaa7e091a0`。
- 离线构建成功；**236 项 JVM 单元测试，0 失败/错误/跳过**。新增 11 项覆盖地点独立、去重、原子索引、损坏/未来格式保护、文件和容量边界、图片采样比例、切城不串图、选图目标固定、40 次模式和面板切换。既有聚焦/坐标/距离/地震/生命周期测试继续通过。
- APK v2 签名通过，原调试证书；74 项场景 assets 与 V95 的 APK 逐字节一致。没有新 8K 图，没有设计样例照片，新增视觉资源仅 1894 字节脚印 PNG。APK 比 V95 增加约 259 KiB，主要是功能代码与 EXIF 支持。
- 215 项源码/资源/构建配置与 Git 工程镜像一致。源 SVG 保存在 `design/footprints-v96/footsteps-approved.svg`。
- 补充 lint 检查未全通过：8 条错误均来自修改前已存在的本机路径转义、调试网络 XML、已有 LocalContext 转 Activity 写法；已逐行与备份核对。本轮没有将其伪称为通过，也没有顺带改动已验收的关闭逻辑。记录在 `worldclock_work/v96/lint-existing-errors.json`。
- 本轮无模拟器/头显运行、屏幕视觉、系统选图返回、GPU 内存或帧率验收。用户要求先交付 APK，后验设备。

## 第一版边界与下一步手动检查

- 首版 16 个足迹地点、每地 200 张照片，每次最多 20 张，单张最大 64 MiB；解码保护拒绝超过 1 亿像素的文件。限制是项目资源预算，不是 SDK 规范。
- 普通及可解码双目文件保存在同一相册；当前按普通二维预览，不声称正确左右眼/空间照片观看。原文件保留，后续技术验证后再接真正双目显示。
- 本机私有副本卸载或清除应用数据后会删除。原系统相册文件不修改、不删除；当前首版相册没有删除/导出管理界面。
- 不同系统是否包含可用照片/文件选择器，以及选图覆盖 Full Stage 后的恢复需真机确认。无法解析的照片显示失败，不导入假图。
- 设备检查：启动并确认原开场；添加不在世界时间列表中的地点；导入横图/竖图、取消和失败重试；连续换城换图并重开应用确认保存；拖地球/Caption；Full→Shared→Full；关闭→重开。检查圆形边框、相册空间位置、遮挡、滚动和长时间内存表现。由用户手动操作，我读取日志；不自动启动/注入手势。

## 恢复与来源

- 修改前完整工程及未提交 Git 工作保存在 `../worldclock_work/v96/pre-footprints-20261026-193435/`，221 项工程文件含原资产、Git 差异和未跟踪 V94/V95 源码/说明；基线哈希同目录上级。没有 reset/stash/覆盖旧工作。
- 本轮没有 Git commit/push；远端仍为 V93；本地待提交内容包括 V94、V95、V96。
- [批准的 Figma 相册](https://www.figma.com/design/dcAVPtoJZ6gLaHjxSk57wj/Earth-Observer?node-id=95-74)，[脚印组件](https://www.figma.com/design/dcAVPtoJZ6gLaHjxSk57wj/Earth-Observer?node-id=98-78)。Figma 原说明中复用世界时间收藏的文字已由用户最新“独立新增地点”要求替代。
- [Android 系统照片选择器与文件选择器回退](https://developer.android.com/training/data-storage/shared/photo-picker)、[授权文档访问](https://developer.android.com/training/data-storage/shared/documents-files)、[按目标尺寸解码](https://developer.android.com/reference/android/graphics/ImageDecoder)。PICO 6.1.9 本地源码确认 `SpatialLaunchActivity → SpatialStubActivity → FragmentActivity`，这证明 Activity Result 接入条件，不代表已验证设备选择器行为。
