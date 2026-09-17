# dmonitor-java-lib

`dmonitor-java-lib` 是 `libDMonitor` 原生库的 Java/JNA 封装，提供 GNSS 监测解算能力。

当前工程内置的原生库：

- `src/main/resources/linux-x86-64/libDMonitor.so`
- `src/main/resources/linux-aarch64/libDMonitor.so`
- `src/main/resources/darwin-aarch64/libDMonitor.dylib`

核心调用入口有两层：

- 推荐：通过 Spring 注入 `MonitorService`
- 直接：通过 JNA 调用 `MonitorLibrary.INSTANCE.startGMonitor(...)`

## 运行要求

- JDK 20 或以上
- Spring Boot 3.2.x
- JNA 5.12.x
- 当前运行系统架构需要有对应的 `libDMonitor` 原生库
- 调用解算时必须提供 license 路径

如果在 IDE 或外部工程中出现 `UnsatisfiedLinkError`，优先检查：

- `libDMonitor.so` 或 `libDMonitor.dylib` 是否在 classpath 资源中
- 当前机器架构是否和资源目录匹配，例如 `linux-x86-64`、`linux-aarch64`、`darwin-aarch64`
- 必要时手动指定：

```bash
-Djna.library.path=/path/to/native/lib/dir
```

## 原生库方法

JNA 接口定义在：

```java
com.navfirst.dmonitor.lib.library.MonitorLibrary
```

当前暴露的方法：

```java
String getGVersion();

String getUuid();

String startGMonitor(MonitorStreamInfo monitorInfo, String license);
```

说明：

- `getGVersion()`：获取算法库版本。
- `getUuid()`：获取当前机器唯一标识，用于申请 license。
- `startGMonitor(...)`：调用原生库执行解算。

`startGMonitor` 返回值格式为：

```text
<solBuf>
<errMsg>
```

第一行是解算结果，第二行是错误信息。`MonitorService` 会自动拆分这两行并转换为 `MonitorData`。
第一行当前为 **30 列**，在 `E N U` 后新增监测站 `X Y Z B L H`。错误信息可以包含多行，服务层保留第一行之后的完整内容。

## 推荐调用方式：MonitorService

在 Spring 环境中直接注入：

```java
import com.navfirst.dmonitor.lib.domains.MonitorTask;
import com.navfirst.dmonitor.lib.services.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class DemoService {

    private final MonitorService monitorService;

    public void run() throws Exception {
        Path brdcPath = Path.of("/path/to/BRDM1400.rnx");
        byte[] brdcBytes = Files.isRegularFile(brdcPath) ? Files.readAllBytes(brdcPath) : null;
        byte[] roverBytes = Files.readAllBytes(Path.of("/path/to/XPJZ01.2026140binRTCM3"));
        byte[] baseBytes = Files.readAllBytes(Path.of("/path/to/XPJZ02.2026140binRTCM3"));

        MonitorTask task = MonitorTask.builder()
                .rtMode(3)
                .timeStart("2026/05/20 00:00:00")
                .timeEnd("2026/05/20 01:00:00")
                .sample(0)
                .vrs(0)
                .roverName("XPJZ01")
                .baseName("XPJZ02")
                .brdcBytes(brdcBytes)
                .roverBytes(roverBytes)
                .baseBytes(baseBytes)
                .outMode(1)
                .minFixedRate(0.75)
                .navSys("1,4,8,32")
                .bds(1)
                .build();

        monitorService.setHandlerData(monitorData -> {
            System.out.println("解算结果: " + monitorData);
            System.out.printf("XYZ(m): %.4f %.4f %.4f%n",
                    monitorData.getX(), monitorData.getY(), monitorData.getZ());
            System.out.printf("BLH(deg,deg,m): %.9f %.9f %.4f%n",
                    monitorData.getB(), monitorData.getL(), monitorData.getH());
        });

        monitorService.startMonitor(task, "/path/to/license.lic");
    }
}
```

必填项：

- `roverBytes`：监测站 RTCM3 数据流。
- `baseBytes`：基准站 RTCM3 数据流。
- `license`：license 文件路径，作为 `startMonitor(task, license)` 第二个参数传入。

可选项：

- `brdcBytes`：广播星历文件流，通常是 BRDM/RINEX 文件内容；可以为空。

重要字段：

| 字段 | 说明 |
| --- | --- |
| `rtMode` | 解算模式：`0` SPP，`2` 动态，`3` 静态 |
| `timeStart` | 开始时间，支持 `yyyy/MM/dd HH:mm:ss` 和 `yyyy-MM-dd HH:mm:ss` |
| `timeEnd` | 结束时间，支持同上格式 |
| `sample` | 采样间隔；通常传 `0` |
| `vrs` | 是否 VRS：`0` 否，`1` 是 |
| `outMode` | 输出模式：`0` 历元解，`1` 单一解 |
| `minFixedRate` | 最小固定率，例如 `0.75` |
| `navSys` | 卫星系统聚合项，例如 `1,4,8,32` |
| `bds` | 北斗频段开关；为 `0` 时会从 `navSys` 中移除 `32` |
| `baseX/baseY/baseZ` | 基准站 WGS84 ECEF 坐标，单位米；分别对应 `rb[0..2]`。未传按 `0` 处理，三项全零时新增 XYZ/BLH 为零 |
| `outFile` | 底层算法输出文件路径，可不传 |

`MonitorServiceImpl` 会做以下转换：

- 校验 `roverBytes`、`baseBytes` 非空。
- 将 `timeStart/timeEnd` 转成原生库需要的 `double[6]`。
- 将 `navSys` 字符串拆分求和后传给原生库。
- `brdcBytes` 非空时自动补 `0` 结尾，避免底层按 C 字符串读取星历时越界。
- 将数据流写入 JNA native memory，并填入 `MonitorStreamInfo` 的指针和长度字段。

需要监测站绝对坐标时，在调用 `startMonitor` 前填入实际基准站 ECEF。例如下面的坐标来自
2026/07/03 的 PSNMJC001 测试样本，其他测站必须替换成对应坐标：

```java
task.setBaseX(-1647115.6013);
task.setBaseY(4602291.3375);
task.setBaseZ(4085428.6662);
```

上面的快速开始示例未设置基准站坐标，因此回调中的 `getX()/getY()/getZ()/getB()/getL()/getH()` 均为 `0.0`。

## 直接调用 SO：MonitorLibrary

如果不使用 Spring，也可以直接调用 JNA 封装。

```java
import com.navfirst.dmonitor.lib.library.MonitorLibrary;
import com.navfirst.dmonitor.lib.library.MonitorStreamInfo;
import com.navfirst.dmonitor.lib.utils.TimeUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class DirectCallDemo {

    public static void main(String[] args) throws Exception {
        Path brdcPath = Path.of("/path/to/BRDM1400.rnx");
        byte[] brdc = Files.isRegularFile(brdcPath) ? Files.readAllBytes(brdcPath) : null;
        byte[] rover = Files.readAllBytes(Path.of("/path/to/XPJZ01.2026140binRTCM3"));
        byte[] base = Files.readAllBytes(Path.of("/path/to/XPJZ02.2026140binRTCM3"));

        MonitorStreamInfo info = MonitorStreamInfo.builder()
                .calMode(3)
                .es(TimeUtils.parseEpoch("2026/05/20 00:00:00"))
                .ee(TimeUtils.parseEpoch("2026/05/20 01:00:00"))
                .ti(0)
                .vrsMode(0)
                .navsys(45)
                .solstatic(1)
                .fixThresh(0.75)
                .rb(new double[]{0D, 0D, 0D})
                .outfile("")
                .build();

        info.setBrdc(appendZeroTerminator(brdc));
        info.setRover(rover);
        info.setBase(base);

        String result = MonitorLibrary.INSTANCE.startGMonitor(info, "/path/to/license.lic");
        String[] lines = result == null ? new String[]{"", ""} : result.split("\\R", 2);

        String solBuf = lines.length > 0 ? lines[0] : "";
        String errMsg = lines.length > 1 ? lines[1] : "";

        System.out.println("solBuf = " + solBuf);
        System.out.println("errMsg = " + errMsg);
    }

    private static byte[] appendZeroTerminator(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        if (bytes.length > 0 && bytes[bytes.length - 1] == 0) {
            return bytes;
        }
        return Arrays.copyOf(bytes, bytes.length + 1);
    }
}
```

直接调用时要注意：

- `MonitorStreamInfo` 的字段顺序必须和原生库结构体一致，不要随意调整。
- `setBrdc/setRover/setBase` 会把 Java `byte[]` 写入 JNA native memory，并设置对应指针和长度。
- `brdc` 可以为空；非空时建议补一个 `0` 结尾。通过 `MonitorService` 调用时会自动处理，直接调用时需要自己处理。
- `startGMonitor` 当前在 Java 中映射为 `String` 返回值，调用方不需要手动解析指针。

## 原生结构体映射

Java 的 `MonitorStreamInfo` 对应原生库的 `GMonitorStreamInfo`：

```c
typedef struct GMonitorStreamInfo {
    int32_t calMode;
    double es[6];
    double ee[6];
    int32_t ti;
    int32_t vrsMode;
    int32_t navsys;
    int32_t solstatic;
    double fixThresh;
    double rb[3];
    const char* outfile;
    const uint8_t* brdc_buf;
    uint64_t brdc_len;
    const uint8_t* rover_buf;
    uint64_t rover_len;
    const uint8_t* base_buf;
    uint64_t base_len;
} GMonitorStreamInfo;
```

对应 Java 字段：

| Java 字段 | 原生字段 | 说明 |
| --- | --- | --- |
| `calMode` | `calMode` | 解算模式 |
| `es` | `es[6]` | 开始时间：年、月、日、时、分、秒 |
| `ee` | `ee[6]` | 结束时间：年、月、日、时、分、秒 |
| `ti` | `ti` | 采样间隔 |
| `vrsMode` | `vrsMode` | VRS 模式 |
| `navsys` | `navsys` | 卫星系统聚合值 |
| `solstatic` | `solstatic` | 输出模式 |
| `fixThresh` | `fixThresh` | 最小固定率 |
| `rb` | `rb[3]` | 基准站坐标 |
| `outfile` | `outfile` | 输出文件路径 |
| `brdcBuf/brdcLen` | `brdc_buf/brdc_len` | 广播星历数据 |
| `roverBuf/roverLen` | `rover_buf/rover_len` | 监测站数据 |
| `baseBuf/baseLen` | `base_buf/base_len` | 基准站数据 |

## 返回结果

`startGMonitor` 返回两行文本：

```text
<solBuf>
<errMsg>
```

`solBuf` 为 30 个空白分隔字段，顺序如下：

```text
startDate startTime gpsDate gpsTime dposMax dposAvg dposStd fixedRate roverEpochRate baseEpochRate E N U X Y Z B L H solStatus solutionType satNum roverSample baseSample roverObsNum baseObsNum fileStatus navStatus navNum offTime
```

通过 `MonitorService` 调用时，`MonitorDataService` 会把 `solBuf` 解析成 `MonitorData`，再通过 `HandlerDataInterface` 回调出去。

| 列号（从 1 开始） | 原生字段 | Java 读取方式 / 含义 |
| --- | --- | --- |
| 1–2 | startDate startTime | `getStartTime()`，拼接为解算窗口开始时间 |
| 3–4 | gpsDate gpsTime | `getGpsTime()`；`getLastObsTime()` 目前取相同值 |
| 5–7 | dposMax dposAvg dposStd | `getDposmax()/getDposavg()/getDposstd()` |
| 8–10 | fixedRate roverEpochRate baseEpochRate | 固定率、监测站及基准站历元完整率 |
| 11–13 | E N U | `getE()/getN()/getU()`，东、北、天基线，米 |
| 14–16 | X Y Z | `getX()/getY()/getZ()`，监测站 ECEF，米，原生文本保留 4 位小数 |
| 17–18 | B L | `getB()/getL()`，WGS84 大地纬度、经度，度，9 位小数 |
| 19 | H | `getH()`，WGS84 椭球高，米，4 位小数；不是正常高 |
| 20–22 | solStatus solutionType satNum | 解算状态、质量类型、卫星数量 |
| 23–24 | roverSample baseSample | 双站采样间隔 |
| 25–26 | roverObsNum baseObsNum | 双站历元数量 |
| 27–28 | fileStatus navStatus | 观测文件状态、星历状态及对应说明 |
| 29 | navNum | `getNavNum()`，星历数量 |
| 30 | offTime | `getOffTime()`，原生解算耗时文本 |

转换在 Go 原生封装中完成，Java 按原值解析为 `Double`。XYZ 为基准站 ECEF 加上
ENU 旋转得到的 ECEF 增量，BLH 表示同一监测站位置。输出小数位数不代表实际定位精度。

- 未传基准站坐标或三项全零时，六个新增坐标字段均为 `0.0`；只有某个分量为零仍可转换。
- 有效基线解且 ENU 为零时，监测站 XYZ 等于基准站 XYZ。
- `Fixed` / `Float` 基线结果可转换；`NONE`、默认失败结果和 `Single`（SPP）的新增六列为零。
  SPP 原有第 11–13 列为经纬高，不能按 ENU 基线理解。
- 转换失败时原生库输出六个零，并在 `errMsg` 说明原因。调用方应结合状态、质量和错误信息判断可用性。

### 同步解析与兼容性

不需要调用原生库时，也可以直接解析第一行结果：

```java
MonitorDataService parser = new MonitorDataServiceImpl();
MonitorData result = parser.handlerDataSync(task, solBuf, errMsg);
```

对应类在 `com.navfirst.dmonitor.lib.services`、`.services.impl`、`.domains` 包中。
`handlerDataSync` 和服务层回调共用同一解析逻辑，支持多个空格、制表符及首尾空白。

- 支持新版 30 列，以及旧版 **Go 封装输出**的 23/24 列。旧版没有 XYZ/BLH 时补 `0.0`；23 列没有 `navNum` 时补 `0`。
- 24 列按 `… solStatus solutionType satNum roverSample baseSample roverObsNum baseObsNum fileStatus navStatus navNum offTime` 解析。
  Java 按该顺序读取采样间隔和后续统计量。
- 带 `reserved/isMoved` 的底层 C 原始结果不属于此兼容契约。同为 24 列时无法仅凭长度区分，
  不应把底层日志直接交给解析器；应使用 `startGMonitor` 返回的 Go 封装结果。
- 空白结果返回 `null`、不触发回调。其他不支持的列数、非法数字及 `NaN/Infinity` 抛出
  `RtkconvException`；数值错误包含列号与字段名，解析失败不触发回调。
- 原生 `errMsg` 非空时完整保留；解析成功且有业务错误的结果仍会回调。

升级时同步替换原生库和 Java SDK。手动按列号解析的业务代码需将 `solStatus` 及后续字段后移 6 位。
新增 `MonitorData` 字段也会出现在对象序列化中；使用 Lombok 全参构造器的代码需重新编译，推荐使用 builder 或 getter。
完整 Word/PDF 说明见 [开发指南](docs/DMonitor-Java-SDK开发指南.docx) 和 [PDF 开发指南](docs/DMonitor-Java-SDK开发指南.pdf)。

本次真实数据回归同时修正了 Go 封装对底层 C 的 24 列结果的映射：`satNum` 后的
`isMoved` 保留列被去掉，末尾缺少的 `navNum` 补零。先前带此错位的 30 列库与新版列数相同，
Java 无法仅凭长度判断，因此必须使用本次配套更新的原生库。例如正确尾部为
`Fixed 1 29 15 15 236 236 0 0 0 1.1s`，含义是双站采样间隔 15 秒、各 236 个历元、状态均为 0，星历数量未提供而补 0。

## 测试

纯解析测试不依赖原生库、RTCM 文件或许可证：

```text
./mvnw -Dtest=MonitorDataServiceImplTest test
```

覆盖 30 列坐标和完整字段映射、23/24 列兼容、六列零值、失败结果、回调、空白分隔、多行错误、异常列数及非有限数字。

真实坐标回归测试使用 2026/07/03 02:00–03:00 的 PSNMJC001/002 样本，需要显式传入数据根目录和许可证：

```bash
./mvnw -Dtest=MonitorCoordinatesNativeTest \
  -Ddmonitor.testDataRoot=/path/to/gnss \
  -Ddmonitor.testLicense=/path/to/license.lic test
```

数据根目录中需包含 `nav/2026/BRDM1840.rnx` 及
`raw/2026/184/02/PSNMJC001.2026184binRTCM3`、`PSNMJC002.2026184binRTCM3`。
测试通过真实 `MonitorServiceImpl` 调用原生库，断言回调次数、时间、Fixed 状态、错误为空，以及有基准站时的 XYZ/BLH 和未传时的零值。
未设置 `dmonitor.testDataRoot` 时此项跳过；显式启用后，夹具缺失或解算不符合预期会失败。

现有 `DmonitorJavaLibApplicationTests.testMonitor` 使用如下本地样例路径：

```text
/Users/wfu/Downloads/GSSK01.2026210binRTCM3
/Users/wfu/Downloads/license.lic
```

运行：

```bash
./mvnw test
```

如果 RTCM3 或 license 文件不存在，`testMonitor` 会跳过。该旧样例使用同一 RTCM 文件作为两站及 BRDC 输入，仅供调用演示；坐标功能回归应使用上面的独立站点样本。

## 常见问题

### 找不到 libDMonitor

确认当前系统对应的原生库在 classpath 中，或者指定：

```bash
-Djna.library.path=/path/to/native/lib/dir
```

### 解算返回错误信息

优先检查：

- license 是否有效
- 如果传入了 `brdcBytes`，确认它是否为对应日期的广播星历
- `roverBytes/baseBytes` 是否为同一时间段的数据流
- `timeStart/timeEnd` 是否覆盖数据流时间范围
- `navSys` 是否包含需要的卫星系统

### 直接调用时没有结果

确认已调用必需数据流：

```java
info.setRover(...);
info.setBase(...);
```

`info.setBrdc(...)` 可选。只设置标量字段但不设置 `rover/base` 数据流时，原生库没有实际观测数据可解算。`brdc` 数据流可为空。
