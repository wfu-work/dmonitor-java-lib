# 测试报告复现

测试使用真实 `MonitorServiceImpl` 和原生库。采集器是独立 JVM 程序，不修改生产接口，也不计入现有 JUnit 用例数。

需要 JDK 20+、Maven，以及含 `numpy` 的 Python 3。DOCX 生成另需 `python-docx`。原始 RTCM、星历和许可证保持在工程外，不提交到仓库。

在仓库根目录执行：

```sh
mkdir -p target/report-work
./mvnw test
./mvnw -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/report-work/classpath.txt
python3 scripts/report-tests/rtcm_audit.py /path/to/gnss docs/test-evidence/2026-09-10/data-audit.json
python3 scripts/report-tests/run_benchmarks.py --data /path/to/gnss --license /path/to/license.lic
python3 scripts/report-tests/summarize.py
```

当前夹具约定为 2026 年第 249 天的 PSYCMM0010/PSYCMM0009，同一小时配对；5 MB 样本按时间连续拼接第 249/250 天的同站数据，并去除经校验的传输分块封装。修改数据集时需要同步修改采集脚本的日期、站点与窗口。

`run_benchmarks.py` 将覆盖上述证据目录的同名结果，历史执行应先归档到其他日期目录。采样数、预热规则、完整帧截取方式、RSS 采样间隔和超时均写在脚本内。各档性能试验串行进行，避免彼此争用 CPU。

`callMs` 从调用 `startMonitor` 之前计时，到同步返回之后结束，包含参数转换、JNA 内存复制、原生解码及解算、结果解析和回调；不含读取文件、创建 JVM 和加载原生库。`queueMs + callMs` 为从该批提交开始到任务完成的时间。串行保护场景中 `callMs` 包含等待互斥锁的时间。

并发使用同一个默认服务、一个始终不替换的回调，通过 `extra=id-round` 关联任务；与串行基线比较 ENU、固定率、状态、历元数和错误等 13 个字段。各档 8 个不同小时任务执行 3 轮，首轮预热，后 2 轮计算性能；互斥对照执行 2 轮。没有模拟位移或修改观测值。

采集器记录业务失败，便于保留异常证据；进程退出码为 0 只代表采集完成。正式质量门禁应同时校验回调数、有限坐标、无错误、文件/星历状态、Fixed 状态与 fixedRate ≥ 0.75。完整报告还要求独立真值和经确认的性能验收指标。
