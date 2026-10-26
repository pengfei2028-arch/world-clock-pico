# V98 — 工具按钮换位与新增相册标记恢复

## 用户要求与反馈

- 交换地震、气温按钮的位置。
- 北京、伦敦添加照片后地球缩略图正常，纽约相册正常但圆形标记缺失；用户进一步确认“相册正常，重开后标记出现”。
- 本轮直接工程修复，不要求另做 Figma。保持已保存照片、足迹地点、原地球资产；无 Git 推送要求。

## 实现

- Dock 顺序更新为：时钟、气温、足迹、时间条、距离、地震、更多。总宽、时间条及 Caption 位置不变。
- 地震面板横向锚点直接对应新按钮中心 +368dp，去除旧宽度适配残留的 -64dp 偏移；菜单仍在按钮上方。
- 新增 FootprintAttachmentMounts：仅在地球场景父节点启用时挂入照片标记及相册附件；确认实体实际属于该父节点后才记为成功。暂停期间和挂载失败保持待处理，恢复后自动重试。先设置正确位置再显示。
- 保持按实际地点数量创建附件，没有预先扩展到16个空窗口，没有新增视觉资产，也没有重建/删除相册或修改照片关联。

## 诊断依据与验证边界

PICO 6.1.9 本地源码链路：
1. SpatialView.onHostLifecycleEvent 在 PAUSE/STOP 时将根实体 enabled=false。
2. Entity.addChild 将父节点的 isEnabled 传给子附件的 onAddToTree。
3. AttachmentPanelLegacyDelegate.onAddToTree 仅在 enabled && uuid!=-1 时设置 isAddedToTree；onActivate 又要求该标志已经为 true。
4. 旧 FootprintRenderer 用 mounted.add(entity) 在真正挂入前记录成功，没有检查父节点状态或挂载结果。若新增附件在系统选图暂停期间进入该分支，返回后只再设置 enabled，而不会重新执行挂入。

这是与“仅新增纽约不显示、重启恢复”吻合且可由源码证明的故障路径。**本轮未连接设备，没有取得用户故障现场日志，不能将其表述成真机已复现或修复已验收。** 保留 V98 mounted 日志供手动验证追踪；原系统选图窗口高度问题仍按 V97 记录待验证。

## 测试和产物

- APK：`../builds/EarthObserver_v98_AlbumPinFix.apk`
- versionCode 98 / versionName 0.98.0，包名 com.wupengfei.worldclock，应用名“地球观察”，arm64-v8a。
- 202709339 字节；SHA256 `09a6e57adbbed383a1efb4eea1fc5e24f33fb771f002bbde9d2e09a1c091f31e`。
- 离线构建及 **251 项 JVM 测试通过，0 失败/错误/跳过**。
- 新增6项：北京/伦敦已挂载而纽约在选图暂停时到达、失败后重试、尚未生成和背面待挂载、100次暂停恢复复用对象、SDK旧回调失效路径的纯逻辑回归、实际城市目录与原网格下纽约实时照片/槽位/经纬回读及转向校验。SDK回调测试使用模型，不冒充设备测试。
- APK v2签名通过，沿用debug证书；74项assets与V97逐字节一致，无新贴图/照片素材/依赖。220项源码/资源/构建配置与Git镜像一致。
- 当前adb无设备，未安装、未自动启动或注入手势，未进行模拟器/头显视觉验收。

## 数据保护与后续验证

- 完整修改前备份：`../worldclock_work/v98/pre-ny-pin-20261026-215517/`，231项工程文件与所有既有Git未提交/未跟踪工作。
- 记录：`../worldclock_work/v98/package-verification.json`、`build-first.log`。
- 未commit/push。远端仍V93，V94–V98本地改动保留。
- 下次由用户手动覆盖安装，检查换位与地震菜单；新加一个尚未添加的足迹地点，立即打开系统选图，返回后确认缩略图无需重启就出现。再重复第二个新地点和取消选图。已有纽约照片无需删除或重加。
