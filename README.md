# 地球观察 · PICO Vibe Lab 提交工程

当前投稿版本是 **V106（0.106.0）**。为保持原投稿地址与构建结构，分支沿用 `release/vibelab-v102`。

完整 Android Gradle 工程直接位于仓库根目录。V102 基线代码和运行资产来自原始提交
`53bf615742a2eb07cfccc519d7400da205aa25d9` 的 `WorldClockSpatial/`。
原始工程、Spatial Editor 源资产、设计文档和版本历史保留在本仓库 `main` 分支。
投稿分支不携带编辑器原稿及其历史；现有 8K/3K 贴图尺寸和地球材质保持。

V105 补充了南海主要岛礁与部分全球群岛的颜色细节，并适度提亮南海补图。原图保留，未夸大岛屿面积，未改光照、云层、夜灯、法线或高光。增加65条离线南海地点搜索；修正城市输入框重复矩形悬停高亮，提示文字保持“城市”。更多菜单提供地图来源与许可。详细边界与验证见 `HANDOFF_V103.md` 至 `HANDOFF_V105.md`。

V106 根据审核反馈统一台湾地区34条城市的显示后缀为“中国”，保留城市ID、时区与地区搜索关键词。地图、天气、气温及地震的UI外链入口全部移除，来源署名改为不可点击的文字；数据更新接口保持。261项测试和构建通过，用户头显体验确认正常并授权重新送审。见 `HANDOFF_V106.md`。

## 构建

- Gradle Wrapper 8.13、Android Gradle Plugin 8.13.2、PICO Spatial SDK 6.1.9。
- Android SDK 35，构建环境使用 JDK 17 或兼容版本；Java/Kotlin 字节码目标为 11。
- 配置本机 `ANDROID_HOME`（或本机未提交的 `local.properties`）。
- Linux：`./gradlew assembleRelease testReleaseUnitTest`。
- Windows：`gradlew.bat assembleRelease testReleaseUnitTest`。
- 唯一 Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`。

Release 不含个人签名配置，由 PICO 平台签名。构建不调用 Spatial Editor、不需要 Node，
只从公开 Maven 仓库解析依赖；预构建的 Spatial bundles 已包含在 `app/src/main/assets/`。

## 数据与权利

城市/时区数据的来源与许可证保留在 `app/src/main/assets/cities/`。
天气使用 Open-Meteo，应用保留来源标注；商业使用须按服务条款评估。
地震使用 USGS 目录；气温请以界面标注的有效时间为准。
岛礁颜色补图包含 **EOxCloudless — EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2025)**，以 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) 非商业方式使用和发布；来源：[EOxCloudless](https://cloudless.eox.at)。选定南海细节还含改编的 Copernicus Sentinel 2024 数据。

岸线数据来自 [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright)（ODbL 1.0）与 [GSHHG 2.3.7](https://www.soest.hawaii.edu/pwessel/gshhg/)（LGPL v3）。派生补图、坐标边界、几何和各自许可见 [来源归档](third_party/island_supplement/README.md)。搜索数据及来源完整保留于 `app/src/main/assets/places/`。这些素材不是无条件商业授权；各部分继续遵守其各自许可。

除明确按第三方许可提供的补图与数据外，本仓库不额外授予代码或其他美术资源的许可。
第三方数据继续遵守各自许可证。仓库不包含个人签名密钥、部署私钥或账户凭据。

## 投稿记录

原任务 1567 的平台日志为 GitHub 下载超时（exit 124），未进入编译。
本分支修正独立发现的仓库根目录问题，并减少提交快照内的非运行文件。
本机构建或下载成功不能保证 PICO 构建机的网络链路；最终以平台实际构建结果为准。

官方要求：https://developer-cn.picoxr.com/document/distribute/pico-vibelab-requirements/
