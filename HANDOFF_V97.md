# V97 — 个人足迹 R4 工程接入

## 授权与基线

用户已确认 R4 Figma，并明确回复“可以 开始改程序吧”。在 V96 上修改，保留未提交的 V94 关闭语义、V95 关闭按钮尺寸和 V96 相册数据。
[批准的 R4 相册](https://www.figma.com/design/dcAVPtoJZ6gLaHjxSk57wj/Earth-Observer?node-id=127-1150)；Figma 仅确定布局，工程继续采用原有 PICO 官方材质、组件和系统 SansSerif。

修改前完整备份：`../worldclock_work/v97/pre-r4-20261026-205312/`，228 项工程文件及 Git 未提交/未跟踪文件、差异、HEAD。没有 reset、stash、提交或推送。

## 操作与变化

1. 点击时间条左侧脚印：打开 208dp 地点列表；底部加号进入 264dp 搜索面板。列表 64dp 行、加号 40dp、搜索结果 40dp，与世界时间的已有布局/添加流程一致。足迹地点继续独立保存。
2. 选择地点或地球上的圆形照片：显示时间条正上方 624×204dp 缩略图面板，不自动展开大图。移除旧右侧相册和相册引线；地球照片标记的短定位针保留。
3. 点击缩略图：打开宽 900dp 的大图查看器，图片区域 852×432dp，横竖图均完整等比显示。白框只强调查看器的当前图片。
4. 大图右上关闭返回缩略图，保留原托盘滚动位置和当前照片；托盘关闭收起相册。点击其他功能或拨地球仍按 V82 规则关闭二三级页面。
5. 再次点击已选脚印打开/收起自己的地点列表，不退出足迹层；时钟等主功能正常切换退出足迹。去除原重复“添加地点”按钮；面板展开时隐藏会与面板重叠的说明文字。
6. 面板改 BOTTOM_CENTER，使用与 Dock 相同位置基准、3.5 倍空间缩放和正向舞台平面；随 Caption 拖动。相册底部距按钮可见顶边 44dp，大图 70dp。选城和查看器切换有约 180ms 淡入。关闭仍立即停止交互，不声称新增完整退出淡出。
7. 地点管理只允许移除空地点；含照片地点暂不支持移除，界面明确标出。没有新增删除照片行为，原文件/相册引用不因误点而丢失。

以上 dp、间距、动画时长均为项目设计，不是 PICO 强制规范。新相册约 204dp，高度在添加中/出错反馈时自然增加。

## 系统照片选择器：已改动与未确认

- V96 静态弱引用初始 LaunchActivity 发起选择，未传可见面板来源。
- V97 在当前 Full Stage 的 ComponentActivity 上注册 Activity Result，传入当前可见相册 View 的 Android source bounds/展开起点。固定注册键，保存并恢复选图目标地点；取消不更换相册，不自动打开大图，导入仍归属发起时地点。
- 保留 V96 的旧结果注册槽以兼容旧 Activity 恢复；新请求全部由当前 Stage 发起。没有使用隐藏接口、未证实 Intent 参数或强制进入外部私有 display。
- 增加启动 owner、display、sourceDisplay、resolver、面板尺寸及返回数量日志，不记录用户照片 URI/文件内容。
- **这不是已验证的绝对 3D 定位修复**：在当前 SDK 6.1.9 公共接口/本地文档中未找到可直接指定外部系统文件选择器世界高度的接口。Android source bounds 属于启动提示，系统可以忽略；初次 Full 中当前 host 也可能仍是 LaunchActivity，因此不能断言仅换 owner 就能解决正下方问题。
- 必须由用户在头显点击“添加照片”观察实际高度，并结合上述日志核对。若仍出现在脚下，需要设备版本/系统窗口实际信息来定位平台摆放问题；不要用 Figma 原型或编译成功冒充已修复。

参考：[Android ActivityOptions](https://developer.android.com/reference/android/app/ActivityOptions#makeScaleUpAnimation(android.view.View,int,int,int,int))、本地 PICO 6.1.9 SpatialStubActivity、AttachmentPanelComponent、SpatialContainer APIs 源码。SDK 内部的 WindowContainer PlacementConfiguration 不能当作外部系统选图窗口的公开定位能力。

## 构建与验证

- APK：`../builds/EarthObserver_v97_FootprintsDock.apk`
- 包名 `com.wupengfei.worldclock`；versionCode 97；versionName 0.97.0；应用名“地球观察”；arm64-v8a。
- 204102834 字节；SHA256：`582fedbe46d21de5be82dd9684edbef99a6a59539f4fd5ce1b7bbfad6030883c`。
- 离线构建成功；**245 项 JVM 测试，0 失败/错误/跳过**。新增 9 项覆盖缩略图显式放大、切城清空旧大图、关闭后的失效回调、列表/搜索分层、脚印重开菜单、50 次功能往返、取消选图、移除图片后的查看器状态、空地点移除保护。
- APK v2 签名验证通过；debug 证书 SHA256 `80786783562575697040fdcdbbbdd20a2e05355571ace5494d30169099422078`。
- 74 项场景 assets 与 V96 APK 逐字节一致；全部 res 原资源未改，没有新增 8K、示例照片、视觉贴图或外部依赖。APK ZIP 条目差异仅应用 dex 与版本 Manifest。
- 实际工程与 Git 镜像 218 项源码/资源/构建文件一致。镜像写入前逐文件核实仍等于修改前基线，未覆盖其他独立编辑。
- 最初旧长路径 Gradle 缓存出现 Windows transform 移动失败；改用已有 `C:/Users/Admin/.gradle` 缓存后离线构建通过。未删除缓存。
- 本轮没有重复 lint；V96 记录的既有 lint 问题仍未宣称解决。
- **当前 adb 无设备。没有进行 V97 模拟器运行、头显佩戴、真实照片导入/取消、空间位置和滚动手感验收。** 用户之前要求先交付 APK，之后验证。不要自动启动或注入设备操作。
- 验证记录：`../worldclock_work/v97/package-verification.json`；构建日志 `build-final.log`；源文件和备份哈希同目录。

## 下次手动验证

1. 直接覆盖安装后打开，已有地点和照片正常。
2. 脚印 → 地点列表 → 加号 → 搜索 → 添加，确认与时间菜单一致且独立。
3. 选城只显示时间条上方托盘；滚动到后排照片 → 放大 → 切图 → 关闭返回，托盘停留原位置。
4. 拖 Caption，托盘/大图随 Dock 一起移动且面向用户；拨地球/其他功能收起页面，无旧引线。
5. 点击添加照片，观察系统文件窗口是否仍在脚下；读取 WorldClockFootprints 的 V97 picker launch/returned 日志。试取消、少量横竖照片、失败重试和重开相册保存。
6. 原世界时间、地震、距离、气温、经纬、Full/Shared 与软件关闭/重开回归。
