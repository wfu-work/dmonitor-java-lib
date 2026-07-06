package com.navfirst.dmonitor.lib.configurations;

import com.navfirst.dmonitor.lib.domains.MonitorTask;
import com.navfirst.dmonitor.lib.services.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 创建：馥溪凝
 * 日期：2022/08/23 12:14
 * 描述：com.navfirst.dmonitor.lib.configurations
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationRunnerStart implements ApplicationRunner {

    private static final Path BRDC_PATH = Path.of("/Users/wfu/Downloads/BRDM1400.rnx");
    private static final Path ROVER_PATH = Path.of("/Users/wfu/Downloads/raw/2026/140/00/XPJZ01.2026140binRTCM3");
    private static final Path BASE_PATH = Path.of("/Users/wfu/Downloads/raw/2026/140/00/XPJZ02.2026140binRTCM3");
    private static final String LICENSE_PATH = "/Users/wfu/Downloads/license-amd64.lic";

    private final MonitorService monitorService;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        this.getUuid();
        this.testMonitor();
    }

    private void getUuid() {
        String uuid = this.monitorService.getUuid();
        log.info("本机许可证标识: {}", uuid);
    }

    private void testMonitor() throws IOException {
        this.monitorService.setHandlerData(monitorData -> log.info("解算结果：{}", monitorData));
        byte[] brdcBytes = Files.isRegularFile(BRDC_PATH) ? Files.readAllBytes(BRDC_PATH) : null;
        MonitorTask monitorTask = MonitorTask.builder()
                .rtMode(3)
                .timeStart("2026/05/20 00:00:00")
                .timeEnd("2026/05/20 01:00:00")
                .sample(0)
                .vrs(0)
                .filterPeriod(7200)
                .processInterval(600)
                .roverName("XPJZ01")
                .baseName("XPJZ02")
                .brdcBytes(brdcBytes)
                .roverBytes(Files.readAllBytes(ROVER_PATH))
                .baseBytes(Files.readAllBytes(BASE_PATH))
                .outMode(1)
                .minFixedRate(0.75)
                .navSys("1,4,8,32")
                .bds(1)
                .build();
        this.monitorService.startMonitor(monitorTask, LICENSE_PATH);
    }

}
