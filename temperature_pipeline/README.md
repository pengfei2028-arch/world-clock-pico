# GFS 2 m 气温生产流程

## 环境

Python 3.10+、numpy、Pillow、NOAA wgrib2（建议 3.1.3 或更高兼容版）。运行时头显不需要 Python、GRIB 或这些依赖。

官方工具：https://www.cpc.ncep.noaa.gov/products/wesley/wgrib2/
Windows 二进制参考：https://ftp.cpc.ncep.noaa.gov/wd51we/wgrib2/Windows10/v3.1.3/
Windows 版需要同目录的 cygwin1、cyggcc_s-seh-1、cyggfortran-5、cyggomp-1、cygquadmath-0 DLL。遵循工具各自的许可证；本仓库不重新分发二进制。

在仓库根目录：

~~~powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r temperature_pipeline\requirements.txt
.venv\Scripts\python.exe temperature_pipeline\build_gfs.py --output temperature_output --wgrib2 C:\tools\wgrib2\wgrib2.exe
~~~

不传 --cycle 时检查最近五个周期的实际发布索引，从最新可用且覆盖所需最后时次的周期开始。只下载 TMP / 2 m above ground 单变量完整全球网格；每次串行获取，有重试，重复原文件走本地缓存。默认从当前整点附近开始，--hours 25 表示 25 小时跨度、26 帧，确保小时对齐后仍覆盖未来 24 小时。不得把包生成时间当作模型起报时间。

复现本次数据（NOMADS 只保留有限近期数据，未来可能需 NOAA 历史归档）：

~~~powershell
.venv\Scripts\python.exe temperature_pipeline\build_gfs.py --output temperature_output --wgrib2 C:\tools\wgrib2\wgrib2.exe --cycle 2026102200 --start 7 --hours 25
~~~

首次单帧验证可用 --start 8 --hours 0。生产任务应省略固定周期，让程序自动选择。获取、变量/时间/网格校验失败时返回非零退出码，旧 latest.json 保持可用。半成品在 .staging-*，最终发布目录不可变；latest.json 通过 os.replace 原子更新。OS 文件锁阻止并发生产者，不依赖长期遗留的人工锁文件。

默认清理超过 48 小时的生成包和原始缓存；允许增大 --keep-hours，不允许小于 48。输出目录须专用于此程序。发布端给已开始下载的客户端留出缓存期；超期请求失败时客户端保留旧内容。

## 校验

~~~powershell
.venv\Scripts\python.exe -m unittest discover -s temperature_pipeline -p test_pipeline.py
.venv\Scripts\python.exe temperature_pipeline\validate_package.py temperature_output\20261022T00Z-f007-032-8e363d150fb6 --wgrib2 C:\tools\wgrib2\wgrib2.exe --raw-cache temperature_output\raw-cache --report validation.json
~~~

validate_package 检查所有数值、PNG、缺失标记、经度保护列、极点保护行、字节数与哈希；可用原 GRIB 对首、中、末时次的北京、纽约、悉尼、格林尼治、日期线及南北极独立查询。

## 本地服务

~~~powershell
.venv\Scripts\python.exe temperature_pipeline\serve.py --root temperature_output --host 127.0.0.1 --port 8765
~~~

http://127.0.0.1:8765/latest.json 是可访问的本地接口。服务只暴露已发布包和 latest.json，不暴露 raw-cache、临时目录或其他工作文件。生产必须在其前面配置 HTTPS 反向代理，并监测数据延迟、磁盘容量及任务退出码；不直接将 Python 调试服务暴露公网。

本机本次真实输出在 ../../worldclock_work/v53/data；工具在 ../../worldclock_work/v53/tools。仓库已包含内置可运行样例，原 GRIB 缓存不进入 Git。

## 客户端连接

客户端构建配置 temperatureBaseUrl 是包服务根地址（不含 latest.json）。默认空字符串，使用内置真实样例并提示自动更新待配置。

~~~powershell
cd WorldClockSpatial
.\gradlew.bat assembleDebug -PtemperatureBaseUrl=https://temperature.your-domain.example
~~~

用真实域名替换示例地址；不得给资源目录 URL 附带个人令牌。生产使用 HTTPS。Debug 仅为 127.0.0.1 / localhost / 10.0.2.2 开放明文测试，其余地址仍要求 HTTPS；release 不含此 debug 网络配置。

本地模拟器或 USB 真机调试可执行 adb reverse tcp:8765 tcp:8765，再以 -PtemperatureBaseUrl=http://127.0.0.1:8765 构建 Debug。网络下载校验通过后才写 current.txt；设备取得时间单独记录在 received-at.txt。应用实际关闭后不继续后台运行。

协议：latest.json 包含 schema、manifest 相对路径及该 manifest 的 SHA-256；manifest 列出 source、variable、level、units、runTime、fetchedAt、generatedAt、网格参数、固定色阶与连续小时 frames。每帧包含 validTime、forecastHour、数值/纹理文件名、大小和 SHA-256，并保留原 NOAA URL / SHA / 获取时间。客户端严格限制路径和下载大小，不允许缺失帧自动补齐。

## 服务器部署待办

本次没有公网服务器、域名或部署凭据，未部署或启用远程定时器。已给出 deploy/ 中三个 systemd 单元与 Caddyfile 示例：

1. 在 Linux 主机安装 Python 环境与 wgrib2，仓库放 /opt/world-clock-pico。
2. 创建专用 worldclock 用户与可写 /var/lib/worldclock-temperature；确认本地运行生成、validate_package 校验成功。
3. 按实际路径调整服务模板；安装 worldclock-temperature.service、worldclock-temperature.timer、worldclock-temperature-web.service。
4. 启用只读服务和 timer；每小时 UTC 的 10 分钟附近检查新周期。任务有文件锁，不会同时写同一发布目录。
5. 配置自己的 DNS、HTTPS 证书与 Caddy 反代；确认 latest.json 能访问且原始缓存不能访问。
6. 以实际 HTTPS 根地址重新构建安装应用，断网/恢复和跨周期更新需在真实网络上验收。

Windows 可用任务计划程序每小时执行同一条 build_gfs.py 命令；程序/参数/工作目录分别填写，不把 Python 服务端口直接开放公网。本次未修改本机任务计划或常驻服务。

共享空间当前未实现，因此不增加 Shared Space 分支；将来可直接复用此协议、网格和固定色阶。
