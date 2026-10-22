# 世界时钟 / 全球气温 · V57 交接

交接日期：2026-10-22（北京时间）。用户要求换一个新对话继续工作，保留当前工程和所有已确认设计。

## 立即接续的任务

用户已体验 V57，先反馈“模拟器没啥问题”，随后反馈“挺好挺稳定的”，并明确授权：“这版上传到 GitHub 上吧”。**这次 GitHub 上传尚未完成。先完成上传，不要重做 UI、配色、气温模块或要求用户再次确认上传。**

新对话继续使用本机 Windows 工作区，不主动切到云端，不新建 Unity 项目。用户偏好简洁中文、直接给本机文件地址、持续说明进展，不喜欢重复确认和长时间无反馈。

## 工程与目录

工作区根目录：

```text
C:\Users\Admin\Documents\Codex\2026-10-20\new-chat-2
```

- 实际运行工程：`WorldClockSpatial`。
- 实际资源编辑器工程：`WorldClockEditor`。
- Git 工作副本：`WorldClockGitHub`，其中也有 `WorldClockSpatial` 与 `WorldClockEditor`。
- 当前技术栈：原生 Kotlin / Compose / PICO Spatial SDK **6.1.9**，不是 Unity。
- 运行时主要代码：`WorldClockSpatial/app/src/main/java/com/wupengfei/worldclock/content/`。
- 阅读根目录、实际工程和 Git 副本中适用的 `AGENTS.md`。历史条目中的旧设计以最新用户确认及本交接为准，例如云层现在已允许、气温默认不透明度现在为 100%。
- 在实际工程修改代码后，必须同步 Git 副本，不能让两份代码分叉。

## 当前可用安装包

```text
C:\Users\Admin\Documents\Codex\2026-10-20\new-chat-2\builds\WorldClock_v57_SharedTimeline.apk
```

- 包名：`com.wupengfei.worldclock`。
- versionCode：57；versionName：0.57.0。
- 文件大小：202601665 字节。
- SHA256：`7108dde7b910d527421a44d76e9a71479ee1fe2ffb3d048c12efbe450a3419cf`。
- 上传准备阶段已重新核对 SHA，与用户体验版的构建记录一致。
- V56 回退 APK：`builds/WorldClock_v56_DeepBlueTemperature.apk`。
- V57 修改前源码：`worldclock_work/v57/baseline/`。
- 测试第一次失败时的源码及日志：`worldclock_work/v57/verification-attempt-1/`。

## 用户已经确认的 V57 设计

1. 世界时间和全球气温共用原来的六小时时间尺；删除独立气温时间尺和文字模式切换面板。
2. 时间尺右侧加入圆形极简温度计，再右侧是原有更多按钮。开启色为 **#745BFF / 82% alpha**。
3. 温标在时间尺正上方，同宽 **528 dp**，高 **10 dp**，首尾圆角半径 **5 dp**。48 dp 圆按钮内使用 20 dp 图标。
4. 温标和独立气温球壳共用 **600 ms 五次平滑淡入淡出**，可以中途反向；模式切换保持时间尺和 caption 的空间位置稳定。
5. 两种模式分别保留时间与视口；城市、地球展示姿态连续，不重播开场。
6. Figma 只用于布局、图标及颜色参考。运行时继续使用官方 PICO Material.Regular / backgroundMaterial、PicoTheme、系统字体及 SpatialHoverStyle.Highlight。不要搬入 Figma 的背景模糊、阴影或另做一套 UI 材质。
7. Figma 已经读取并落地，无需为了上传重新连接。文件 key `dcAVPtoJZ6gLaHjxSk57wj`，审阅区域 `2:34`，开启态 `3:87`。最新参数保存在 `docs/designs/shared-timeline/approved-v57.json`；`figma-state.json` 是历史设计快照，不是最新参数来源。

## 气温模块已实现与边界

- 使用真实 NOAA GFS 全球 **2 m 空气温度**，包含海洋上方空气温度；不使用海水温度或少量城市拼图。
- 内置真实样例：起报 `2026-10-22T00:00:00Z`，有效时刻从 07Z 至次日 08Z，共 26 帧。以实际 manifest 为准，过期内容不能标成最新。
- 数据处理、单位校验、经纬度映射、数值网格、小时缓存与城市同源查询已实现。文件和部署模板位于 `temperature_pipeline/`。
- 在线域名、HTTPS 服务与定时更新**尚未部署**；这不是每小时新全球实测。不要将本地样例说成在线实时气象。
- 气温为独立球壳，半径是地球的 **1.003 倍**。独立数值贴图与固定配色，不降低原地球 8K 资产质量。
- 默认叠加强度 **100%**；强度控制放在更多菜单中，主场景不增加说明文字。
- 固定色阶：0–5°C 深蓝，5–10°C 亮蓝，10–20°C 饱和青色，20°C 起转黄；25°C 以上按 25/28/31/34/37/40°C 分档。不得按每帧极值自动拉伸配色，或为扩大冷区改造真实温度。
- 最新材质和球壳仍叫 `TemperatureV56` / `TemperatureShell_V56`，相关日志仍带 V56；这表示沿用的材质版本，**不表示 APK 是旧版**。V57 改的是 UI、共用时间状态和渐变。

## 必须保留的地球与交互

- StageStyle.Mixed；前方约 100° 黑色虚拟区域，边缘渐隐到 VST，身后保留 VST。
- 原高清日图、夜灯、法线、海陆高光与云图保持独立，不降分辨率。
- 日图使用用户提供的原始 8K AI 颜色图（V51），高光使用用户原始 8K AI 高光遮罩（V52 RAW + LINEAR 绑定）。不要误恢复为对话附件中的 2K 副本。
- 真实 UTC / 预览 UTC 驱动太阳与晨昏线；23.44° 地轴。展示自转时地球和太阳保持对应关系，不硬编码北京时间补偿。
- 手动 / 时间轴释放后等待 3 秒；城市聚焦后等待 5 秒；回正至少 4 秒、峰值 24°/s；待机自转 3°/s。
- 捏住或操作时间轴时暂停展示自转及回正，夜灯区域噪波闪烁继续。拨动按用户视野水平、垂直方向响应，保留防抖和速度限制。
- 云层是原 Blender 静态云图。完整开场结束后 1.4 秒淡入；操作地球、时间轴或选城时淡出，回正恢复。气温模式隐藏遮挡数据的云层，退出恢复。
- 城市铆钉真实位置不变，详情面板保持舞台竖直、向右展开；近邻城市上下避让，不因太近而隐藏。原城市功能和退出行为保持。
- 不顺手改太阳、高光、开场、字体、hover 或 caption。当前版本用户已确认稳定。

## 验证状态：准确区分

- 用户在普通 Windows 会话执行 `tools/build-local-v57.cmd`：构建成功（29 秒），18 套件、**98 项单元测试通过**，0 失败/错误/跳过；其中 6 项为新增共用时间条测试。
- V56 与实际 V57 APK 内 **71 项 assets 全部 SHA256 相同**。
- 上传前核对实际工程和 Git 副本：**147 个运行时源码、测试、资源及 app 构建配置文件完全一致**。
- 用户手动体验：明确确认模拟器没有问题、版本稳定。这可以作为手动体验接受记录。
- 自动集成首次运行失败：`SharedTimelineIntegrationTest` 在 instrumentation 线程读取 PICO `OpacityControllerComponent.opacity`，SDK 要求主线程。首次日志证明应用启动并提交气温帧，但不是自动通过。
- 已修正测试源码：SDK 属性读取与等待谓词统一 `runOnMainSync`；渐变采样包含数据准备阶段；等待完成有超时。只改测试，没有因此改变用户体验 APK。
- **修正后的自动测试尚无重编或重跑通过记录。** 本机 `builds/v57-verification/integration.log` 仍是 18:19 的首次失败记录。不要把用户手动反馈写成自动测试通过。
- Android UiAutomation 截图全黑，不是 PICO XR 画面的有效证据。脚本已将其标记为 `-android.png` 诊断图，并在已安装 pico-cli 可用时尝试官方截图。
- V57 真机性能、手势或佩戴观感没有独立验收证据；用户最近一次反馈明确说的是模拟器。
- 详细记录：`docs/global-temperature-v57.md`、`docs/validation/v57-validation.json`。

## GitHub 的准确状态（交接时重新核实）

仓库：`https://github.com/pengfei2028-arch/world-clock-pico`，**私有**，GitHub 连接器确认有 push/admin 权限。

- 远端 `main` 仍为 **V52**：`e8fc208b826cda7c150951f4574626d951b03f95`。
- 对应基准 tree：`2cd1928c9f33431e7336163a3eba163a139c215c`。
- 本地当前分支：`feature/global-temperature-v53`。
- 本地 HEAD（V56）：`c0b31833ae5998303cdfd668bc0dc306d2084d99`。
- 本地 V53–V56 历史依次为 `ee6626a`、`37ec2dd`、`4609d4b`、`c0b3183`，尚未上传。
- **V57 源码及本次说明文档仍是本地未提交修改，不要 reset、clean、checkout 覆盖或重新克隆替代它们。**
- 已更新 README、CHANGELOG、两份 Git 副本 AGENTS、V57 文档和验收 JSON，记录用户接受状态与仍未通过的自动测试。
- 计划发布 `main` 并保存 `release/v0.57.0` 回退分支；原远端历史和 `v0.50.0 / v0.51.0 / v0.52.0` 标签保持。
- README 提及的 `release/v0.57.0` 是待创建目标，**交接时尚未创建**。

### 上传遇到的限制

本机会话中的 Git 默认 Schannel TLS 报 `SEC_E_NO_CREDENTIALS`。一次仅对该命令使用 OpenSSL、保持证书校验后，进一步报 GitHub CLI 的 `config.yml` 访问被拒绝，无法获取账户用户名。不要禁用证书校验、导出凭据或尝试绕过文件权限。

已连接的 GitHub 插件正常可读写。曾开始通过正式 GitHub API 上传 blob：

- 唯一确认完成的 blob 是 `.gitignore`，SHA `ceee378e034708dd1d2b8f2e5451b96daa7ae24a`。
- 没有创建 tree / commit，也没有移动任何远端分支。其他大文件是否形成未引用 blob 未确认，不能算成已上传版本。
- 用户要求交接时，原 functions exec 上传单元已经不存在；没有可继续等待的任务。不要依赖旧 cell ID 或 functions store。
- 准备清单 `worldclock_work/v57/github-upload-manifest.json` 当时为 **133 个文件、103768064 字节**。本交接文档是之后新增的，清单需要重新生成并校验，不能照旧清单漏传。
- 计划中的 `github-upload-progress.json` 没有落盘；不能据此推断批量上传完成。

### 新窗口推荐续办顺序

1. 读取本交接及适用 AGENTS；检查本机文件仍在、Git 状态与远端 HEAD，保护未提交改动。
2. 若普通本机 Git 认证已恢复，优先提交并正常 fast-forward 推送，保留 V53–V57 历史。不能强推远端，不改变私有属性。
3. 若仍被当前会话认证权限阻塞，可用已连接 GitHub API 上传完整快照；以当前远端 main 为 parent，先校验全部文件/blob，再建 tree、commit，最后带 expected_sha 更新 main，保存版本分支。原有远端历史必须保留，先上传文件不等于发布成功。也可准备可审阅的单次本机推送脚本，必要时由用户正常会话运行；不要无休止重试权限限制。
4. GitHub 插件可发现 `create_blob`（base64）、`create_tree`、`create_commit`、`create_branch`、`update_ref`、`fetch_commit`；通过 ALL_TOOLS 查询当前参数定义。不能假定工具支持标签创建，当前回退目标是分支。
5. 此前 exec_command 一次输出 800000 字符时偶尔静默丢失尾部，读二进制分块必须检查长度。300000 字节 → 400000 个 Base64 字符的分块曾完整返回，块长取 3 的倍数；只能在长度完整时拼接。返回 blob SHA 必须与本地 Git blob SHA 一致。避免向对话输出巨大 Base64。
6. 发布后通过 GitHub 再读回 main、版本分支、版本号及关键文件，确认真正上传完成；最后给用户仓库链接、版本/提交号。
7. 本地 Git 与 API 创建的远端提交可能不同，务必记录这种状态，不伪造本地 origin/main 或声称两边已经同步。不要为修正元数据破坏现有本地历史。

## 本机工具与执行限制

- Git：`C:\Program Files\Git\cmd\git.exe`。
- ADB：`C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe`。
- JBR：`C:\Program Files\Android\Android Studio\jbr`。
- 手动构建：`WorldClockGitHub/tools/build-local-v57.cmd`。
- 手动模拟器验证：`WorldClockGitHub/tools/verify-local-v57.cmd`；只面向 `emulator-5554`，先离线重编 Android 测试包，再安装运行，失败即停止对应步骤。
- 当前受限会话运行 Java/ADB 曾被拒绝，扩大权限的自动审批也曾拒绝。普通用户会话手动构建可以成功。不要把会话权限问题归咎于用户 SDK 坏了，也不要通过其他工具/终端绕过同一限制。
- 本次新开窗口的 `codex_app.list_projects` 调用报错，未取得 projectId，尚未自动创建新窗口。用户可在同一个本机工作区手动新开对话并让新助手先读此文件。

## 继续开发时需要知道的 V57 文件

`ClockPanels.kt` / `ClockScene.kt` / `HomeStage.kt`：共用时间尺、模式切换、面板锚点。

`TemperaturePanels.kt`：温度计与温标；`TemperatureState.kt`：独立气温时间状态；`TemperatureVisibility.kt`：可逆 600 ms 渐变；`TemperatureRenderer.kt`：独立球壳、纹理与原地表高光恢复。

`app/src/main/res/drawable/ic_temperature.xml`：Figma 三条矢量路径。

`SharedTimelineTest.kt`：6 项单元测试已通过；`SharedTimelineIntegrationTest.kt`：线程修正后的自动用例尚待实际重跑。

当前优先级是把用户确认的这一版上传备份。没有新的视觉或功能改造授权，完成同步后等待用户新需求。
