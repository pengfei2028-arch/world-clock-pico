# 地球观察 · PICO Vibe Lab 提交工程

这是 V102（0.102.0）的独立投稿分支 `release/vibelab-v102`。

完整 Android Gradle 工程直接位于仓库根目录。应用代码和运行资产来自原始提交
`53bf615742a2eb07cfccc519d7400da205aa25d9` 的 `WorldClockSpatial/`。
原始工程、Spatial Editor 源资产、设计文档和版本历史保留在本仓库 `main` 分支。
投稿分支不携带编辑器原稿及其历史；没有降低贴图分辨率，也没有修改应用功能或视觉。

本次投稿适配仅补充 `.bundle` 不压缩的 Android 资源打包配置。所有运行资产仍保留。

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
这是私人项目的投稿源码，不额外授予代码或美术资源的公开分发许可。
第三方数据继续遵守各自许可证。仓库不包含个人签名密钥、部署私钥或账户凭据。

## 投稿记录

原任务 1567 的平台日志为 GitHub 下载超时（exit 124），未进入编译。
本分支修正独立发现的仓库根目录问题，并减少提交快照内的非运行文件。
本机构建或下载成功不能保证 PICO 构建机的网络链路；最终以平台实际构建结果为准。

官方要求：https://developer-cn.picoxr.com/document/distribute/pico-vibelab-requirements/
