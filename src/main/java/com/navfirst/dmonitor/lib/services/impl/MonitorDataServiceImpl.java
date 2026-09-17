package com.navfirst.dmonitor.lib.services.impl;

import com.navfirst.dmonitor.lib.domains.MonitorData;
import com.navfirst.dmonitor.lib.domains.MonitorTask;
import com.navfirst.dmonitor.lib.enums.NavErrorEnum;
import com.navfirst.dmonitor.lib.enums.ObsErrorEnum;
import com.navfirst.dmonitor.lib.exceptions.RtkconvException;
import com.navfirst.dmonitor.lib.services.HandlerDataInterface;
import com.navfirst.dmonitor.lib.services.MonitorDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 创建：馥溪凝
 * 日期：2022/04/10 13:40
 * 描述：com.navfirst.dmonitor.lib.services.impl
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class MonitorDataServiceImpl implements MonitorDataService {

    private HandlerDataInterface handlerDataInterface;

    @Override
    public void handlerData(MonitorTask monitorTask, String data, String errMsg) {
        MonitorData monitorData = handlerDataSync(monitorTask, data, errMsg);
        if (monitorData != null && this.handlerDataInterface != null) {
            this.handlerDataInterface.handlerData(monitorData);
        }
    }

    @Override
    public MonitorData handlerDataSync(MonitorTask monitorTask, String data, String errMsg) {
        if (StringUtils.isBlank(data)) return null;
        if (monitorTask == null) {
            throw new RtkconvException("监测任务不能为空");
        }
        String[] splits = data.trim().split("\\s+");
        if (splits.length != 30 && splits.length != 24 && splits.length != 23) {
            throw new RtkconvException("不支持的解算结果字段数：" + splits.length + "，需要 30 列或旧版 Go 封装的 23/24 列");
        }
        MonitorData monitorData = getMonitorDataBySplits(monitorTask, splits);
        if (StringUtils.isNotBlank(errMsg)) {
            monitorData.setErrMsg(errMsg);
        }
        log.debug("解算结果：{}", data);
        return monitorData;
    }

    private MonitorData getMonitorDataBySplits(MonitorTask monitorTask, String[] splits) {
        boolean hasCoordinates = splits.length == 30;
        int statusIndex = hasCoordinates ? 19 : 13;
        int fileStatus = parseInt(splits, statusIndex + 7, "fileStatus");
        int navStatus = parseInt(splits, statusIndex + 8, "navStatus");
        String gpsTime = splits[2] + " " + splits[3];
        MonitorData.MonitorDataBuilder builder = MonitorData.builder()
                .baseName(StringUtils.trimToEmpty(monitorTask.getBaseName()))
                .roverName(StringUtils.trimToEmpty(monitorTask.getRoverName()))
                .startTime(splits[0] + " " + splits[1])
                .gpsTime(gpsTime)
                .lastObsTime(gpsTime)
                .dposmax(parseDouble(splits, 4, "dposMax"))
                .dposavg(parseDouble(splits, 5, "dposAvg"))
                .dposstd(parseDouble(splits, 6, "dposStd"))
                .fixedRate(parseDouble(splits, 7, "fixedRate"))
                .roverEpochRate(parseDouble(splits, 8, "roverEpochRate"))
                .baseEpochRate(parseDouble(splits, 9, "baseEpochRate"))
                .E(parseDouble(splits, 10, "E"))
                .N(parseDouble(splits, 11, "N"))
                .U(parseDouble(splits, 12, "U"))
                .solStatus(splits[statusIndex])
                .solutionType(parseInt(splits, statusIndex + 1, "solutionType"))
                .satNum(parseInt(splits, statusIndex + 2, "satNum"))
                .roverSample(parseInt(splits, statusIndex + 3, "roverSample"))
                .baseSample(parseInt(splits, statusIndex + 4, "baseSample"))
                .roverObsNum(parseInt(splits, statusIndex + 5, "roverObsNum"))
                .baseObsNum(parseInt(splits, statusIndex + 6, "baseObsNum"))
                .fileStatus(fileStatus)
                .fileStatusDesc(ObsErrorEnum.getValue(fileStatus))
                .navStatus(navStatus)
                .navStatusDesc(NavErrorEnum.getValue(navStatus))
                .navNum(splits.length == 23 ? 0 : parseInt(splits, statusIndex + 9, "navNum"))
                .offTime(splits[splits.length - 1])
                .rtMode(monitorTask.getRtMode())
                .filterPeriod(monitorTask.getFilterPeriod())
                .processInterval(monitorTask.getProcessInterval())
                .vrs(monitorTask.getVrs())
                .taskType(monitorTask.getTaskType())
                .outMode(monitorTask.getOutMode())
                .navSys(StringUtils.isNotBlank(monitorTask.getNavSys()) ? monitorTask.getNavSys() : "1,4,8,32")
                .extra(monitorTask.getExtra());
        if (hasCoordinates) {
            builder.X(parseDouble(splits, 13, "X"))
                    .Y(parseDouble(splits, 14, "Y"))
                    .Z(parseDouble(splits, 15, "Z"))
                    .B(parseDouble(splits, 16, "B"))
                    .L(parseDouble(splits, 17, "L"))
                    .H(parseDouble(splits, 18, "H"));
        }
        return builder.build();
    }

    private double parseDouble(String[] splits, int index, String field) {
        try {
            double value = Double.parseDouble(splits[index]);
            if (Double.isFinite(value)) return value;
        } catch (NumberFormatException ignored) {
            // Report the wire field instead of leaking an unlabelled parsing error.
        }
        throw invalidField(index, field, splits[index], "有限浮点数");
    }

    private int parseInt(String[] splits, int index, String field) {
        try {
            return Integer.parseInt(splits[index]);
        } catch (NumberFormatException ignored) {
            throw invalidField(index, field, splits[index], "整数");
        }
    }

    private RtkconvException invalidField(int index, String field, String value, String expected) {
        return new RtkconvException("解算结果第 " + (index + 1) + " 列 " + field
                + " 应为" + expected + "，实际为：" + value);
    }

    @Override
    public void setHandlerData(HandlerDataInterface handlerDataInterface) {
        this.handlerDataInterface = handlerDataInterface;
    }

}
