# V88 / 0.88.0 最终调校

用户接受V87：“目前找不到其他问题了”，随后授权晨昏柔化、距离与时间互斥、头显Caption拖栏跟手优化。本版已构建并安装模拟器；用户已手动打开，实际地球、夜灯及地震图层出现，开场完成。用户本轮回复“都很好，非常好”；接受模拟器体验，随后仅要求地震波纹范围更明显。头显未连接，仍不等同于新增真机手感验证。Git远端仍为已验收V82；V83–V88未提交/推送。本轮不增加模块、不重做UI。

## 本版改动

### Full晨昏

参考Google Earth Studio官方Time of Day说明的太阳角度、昼夜纹理及晨昏层次：https://earth.google.com/studio/docs/advanced-features/special-attributes/ 。官方没有公开本项目可复用的着色公式；本版是适配PICO现有PBR材质的近似，不声称复制Google算法或物理大气散射。

原Surface保留PBR基色、海陆roughness/IOR、法线、高光、真实UTC太阳和夜灯。原夜灯0°到−12°五次平滑与局部闪烁不变。新增7个MaterialX节点，在夜灯转为RGBA之前相加一项原昼图颜色的低强度柔光：

- d = dot(几何世界法线, 真实太阳方向)。
- w = sin(24°)，fill = max(0, w − abs(d))² / (4w)。
- fill在±24°之外严格为0，地平线最大约0.101684；不抬亮深夜，不影响高太阳高度的原PBR。
- 对单位Lambert参考 max(0,d) + fill，−w、0、+w处一阶斜率连续。实际PBR/曝光仍需手动画面验收；不是仅凭数学检查就宣称观感达标。
- 无额外灯源、IBL、蓝色染色球壳或纹理。只调整Full；Shared现有独立Unlit软晨昏材质保留。
- 运行时集中参数为DayNightLighting.twilightWidthDot/twilightGain；加载后读取材质参数校验。V88启动日志含compact twilight。

用官方spatialbundle编译小图占位的独立probe工程，再将编译后的shader、参数说明和4项新增uniform写入原Earth.bundle。占位纹理绝不进入APK。4个原纹理payload、网格、scene和AssetInfo全字节不变；既有R8高光/正常RAW法线绑定保留。可复现工具：tools/build_twilight_v88.py、tools/patch_twilight_bundle.py；可编辑源EarthShaderV88.usda/EarthSwanV88.usda。运行时仍用原EarthSwanV51层级路径，避免坐标/城市/距离/地震行为改变。

编译示例：spatialbundle pack -s <work>/probe/Sources/Scenes -p <work>/probe -o <work>/Earth-candidate.bundle --formats usda --compress-textures。此SDK的--compress-textures是开关，不追加astc4x4参数。

### 模式互斥

距离仍复用世界时间的太阳/时间状态，但有独立主入口选中判断。启用尺子熄灭时钟、隐藏城市时间卡片并使用已有精简城市标签；时钟入口退出距离并打开原城市菜单。任何显式主模块切换（包括再次进入WORLD_TIME）都先清理距离端点；距离从地震/气温进入时先回世界时间底层。拖公共时间条也退出距离并进入世界时间；只拖动地球仍保留两城选择。预览UTC和时间尺视口不因开关距离而重置。

### Caption拖动

Full自定义Caption手柄，非Shared系统原生CaptionBar。原固定35ms低通改速度自适应8–24ms：快拖低延迟，慢动保持防抖；保留0.35dp噪声带、Stage固定平面、按下时锁定深度增益1–6倍和现有位移边界。没有预测或超调。超过200ms跟踪断流时重锚到当前可见位置，防突然追到旧目标。松手前读取有效末次输入（全零丢失姿态跳过），保留最终目标而不丢弃残余位移。现有UI位置/尺寸/官方材质不变。

## 验证

- 217项JVM检查：0失败/错误/跳过。新增7项覆盖模式互斥、100次往返保留时间、拖球保留距离、拖尺退出、60/72/90/120Hz快拖延迟/不超调、末帧释放、跟踪中断重锚。既有静止900次噪声与30/60/90Hz拖动测试继续通过。
- SDK材质编译成功；直接求值编译MaterialX图20001个太阳角度采样，确认紧凑支撑、非负、参考明暗单调、三处斜率连续。与旧图逐节点对比：旧节点仅NightRGBA输入改接SurfaceEmission，其余旧节点一致。
- APK74项assets与工程SHA一致；相对V87仅Earth.bundle改变，内部4个纹理数据、网格、场景/资源表均保持原字节，无新增8K或其他贴图。构建、APK v2签名通过。
- 覆盖安装emulator-5554成功；系统报告versionCode=88、versionName=0.88.0。未清空应用数据，未自动启动。
- 当前只有模拟器连接；18:38:49用户手动启动PID8848，实际截图可见地球、夜灯、地震菜单/标记/波纹；开场完成日志true。本次采集未发现应用致命异常。晨昏画面并非受控同时间/同视角前后对比，仍待用户反馈；未验证头显跟手感、功耗或长时间内存，不能写成最终体验已通过。

## 交付与回退

APK：../builds/WorldClock_v88_FinalPolish.apk，203286908字节。

SHA256：e7dcbd8debaf0bd8c9d39a2c5dae7a0c45910db2607f3e249a78481cf0ecbded

可恢复快照：../worldclock_work/v88/baseline-v87-20261025-182609/，包含实际工程和Git镜像的全部app/src、版本文件及文档；V87 APK和先前快照保留。版本、资源、构建及材质报告在worldclock_work/v88。原始E:/Work源资产未改动。README、AGENTS已记录V87接受和V88待验证状态。

下一步由用户手动打开V88：对比晨昏、夜灯和海面高光；尺子/时钟往返；连接头显后安装同一APK检验Caption快拖/慢拖/静止/释放。没有用户后续授权不自行上传GitHub，不自动操控设备。

## 用户最新验收

用户回复“都很好，非常好”，随后要求放大地震波纹范围。保留本版为已确认回退点；后续V89仅调地震波纹，不继续改变已确认的晨昏/模式/Caption。头显未连接，未新增佩戴验收。
