# V99 — 纯提示文字不阻挡地球输入

## 用户反馈与范围

用户报告“点选地球上的照片查看相册”附近有隐形面板，无法操作后面的地球；允许删除此提示，其他功能提示可保留但不能拦截输入。本轮保留 V98 按钮换位、照片标记恢复和全部既有相册数据，不新增功能或设计页面。

## 实现

- 删除“点选地球上的照片查看相册”；空足迹状态仍保留“先添加一个足迹地点”。
- TimePanel 原有 912×222dp 尺寸/位置不变，根布局改为不参与空间命中的显示层。距离选点提示、地震周期文字、气温状态、系统时间提示及透明留白共用该层。
- 气温图例、距离公里读数、经纬刻度/坐标读数、城市时间与引线的显示区域同样不参与空间命中。坐标读数原来的空回调按钮改为无点击行为的官方玻璃展示块。
- 保留实际控件自己的原生输入层：Dock 按钮、时间条、Caption 拖动/关闭、返回现在/总览、城市选择、照片缩略图、相册与菜单。没有全局禁止输入，没有伪造或转发手势。
- 新增 SpatialInput.kt，使用 SDK 6.1.9 公开的 zOffset 消费者与 enableSpatialHittestProvider(false) 成对配置。仅 provider 本身不能创建显示层。
- 淡入淡出与圆角在绘制中处理，避免另外加 alpha()/clip() 生成默认可命中的图层；玻璃自身的 backgroundMaterial 图层另配 false provider。小读数仅淡入淡出时使用离屏透明混合，Dock 本身不会创建该透明混合。
- 添加 SDK 要求的 application metadata：pico.spatial.use_experimental_api=1；最终 APK 已核对为整数1。最终代码没有反射、内部 API 访问或内部可见性抑制。

## 能力依据与限制

- 本地 SDK 源码 SpatialHittestConsumerNode 把 provider 的 enable 写入图层 isCollisionEnable；SpatialZOffsetModifierNode 继承该消费者。
- BackgroundMaterial 有独立的命中消费者，需要独立关闭。
- [PICO 6.1.9 官方接口说明](https://developer-cn.picoxr.com/spatial-api/6.1.9/spatialui/foundation/com.pico.spatial.ui.foundation.spatialhittest/enable-spatial-hittest-provider.html) 明确 provider 需要祖先消费者，且可退出空间命中。
- **当前 adb 无设备，未取得本次问题的运行日志/画面，也未做实际射线穿透或头显手势验收。** 编译与 JVM 检查不能证明系统级命中行为；尤其需验证只读层可穿透、子按钮仍可操作、读数玻璃的圆角/淡出不变。

## 构建与交付

- versionCode 99 / versionName 0.99.0，包名 com.wupengfei.worldclock，arm64-v8a。
- APK：`../builds/EarthObserver_v99_PassThroughHints.apk`
- 大小 204127486 字节，SHA256 `8aed9ba5357e6b91b90071a4b2fadce3d8f655a2b1f17c54e975936fd94dffb4`。
- 离线 assembleDebug 与现有251项 JVM测试通过，0失败/错误/跳过；没有为本次 UI 参数增加镜像式测试。
- APK v2签名验证通过，沿用既有 debug 证书。
- 74项 assets 与 V98 字节一致，无新照片、贴图、依赖；221项工程源码/资源/构建配置与 Git 镜像一致。
- 备份：`../worldclock_work/v99/pre-hint-input-20261026-221619/`，233项工程文件及此前所有未提交/未跟踪 Git 内容。未提交、未推送 GitHub；V94–V99 本地修改保留。
- 记录：`../worldclock_work/v99/build-final.log`、`package-verification.json`。

## 下次由用户手动验证

1. 开足迹后收起菜单，在旧提示的位置拖动地球；相册缩略图仍应可点。
2. 距离模式在“请选择第二座城市”文字位置按住拖动；城市按钮仍能选择第二点。公里读数上方/文字处也可操作背后地球。
3. 气温色条、地震周期文字、经纬文字/坐标卡、城市日期/引线处测试地球拖动；真实按钮、时间条和 Caption 不应穿透误触地球。
4. 时间预览与地震回放时分别检查“回到现在”和“返回总览”仍可点；反复切模式后不存在残留输入遮挡。

V97 系统选图窗口高度、V98 新地点照片即时标记等尚未验收项目保持原记录，不能借本轮构建记为通过。继续由用户操作设备，不自动启动或注入手势。
