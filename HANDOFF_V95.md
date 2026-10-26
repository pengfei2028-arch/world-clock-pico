# V95 · 放大 Caption Bar 关闭按钮

用户已确认V94头显关闭重开正常，并批准Figma放大稿、要求打包。本轮仅接入已确认的按钮尺寸，保留V94生命周期修复。

## 变更

- 可见圆底及PICO官方 `ic_sui_close` 从16dp放大至24dp（线性尺寸+50%）。仍为白色图标、18%白色透明圆底，原悬停显现及关闭语义不变。
- 点击区32×42dp改为48×48dp，按钮中心从手柄右侧72dp调整至80dp。拖动命中宽仍112dp，两者在x=56dp处相接，无面积重叠。
- Caption行及拖动命中高度改为48dp；可见手柄仍88×16dp。保持同高，不移动上方时间条或新增界面。
- 只修改 `ClockPanels.kt` 与版本配置，未新增资产。Figma评审说明没有导入应用界面。
- [确认的Figma对比稿](https://www.figma.com/design/dcAVPtoJZ6gLaHjxSk57wj/Earth-Observer?node-id=90-76)，提案组件90:65。

## 产物与验证

- `../builds/EarthObserver_v95_LargerCloseButton.apk`，0.95.0 / versionCode95，202395019字节。
- SHA256：`98a92a9c0094943b6e3c116f7058cbebdbfa34e352a4d46884dd304a53fe34e4`。
- 离线构建及225项既有单元测试通过，0失败/错误/跳过；APK v2签名通过。尺寸修改没有新增模拟实现本身的单元测试。
- 74项场景资产及APK全部 assets/res 条目与V94字节一致，208项工程文件与Git镜像一致。
- 已保留数据覆盖安装PICO B3110头显PB3310PGL7200028B，包管理器确认0.95.0，lastUpdateTime为2026-10-26 16:06:48。未自动启动或注入手势；模拟器仍为V94。
- 比例与命中区域已按源码/Figma核对；实际头显按钮观感、点击和拖动体验待用户手动验证，不能把安装成功当作运行时验收。

## 保护与回退

- `../worldclock_work/v95/pre-caption-update/` 保存修改前V94源码、构建配置、AGENTS、交接与未提交Git差异；原208项文件哈希在同级baseline记录。V94未提交修改已保留。
- V94 APK保留；关闭重开的头显修复已获用户确认。
- 本轮未提交或上传GitHub。远端发布仍为V93，V94–V95均在本地，待用户授权上传。
