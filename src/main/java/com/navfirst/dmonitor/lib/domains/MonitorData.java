package com.navfirst.dmonitor.lib.domains;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建：馥溪凝
 * 日期：2022/04/10 13:52
 * 描述：com.navfirst.dmonitor.lib.domains
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorData {

    /**
     * 观测站
     */
    private String roverName;

    /**
     * 基准站
     */
    private String baseName;

    /**
     * 解算窗口开始时间，格式 yyyy/MM/dd HH:mm:ss
     */
    private String startTime;

    /**
     * 解算坐标时间
     */
    private String gpsTime;

    /**
     * 最后一个历元时间
     */
    private String lastObsTime;

    /**
     * 东向坐标
     */
    private Double E;

    /**
     * 北向坐标
     */
    private Double N;

    /**
     * 天向坐标
     */
    private Double U;

    /**
     * 监测站 ECEF X，单位米
     */
    @Builder.Default
    private Double X = 0D;

    /**
     * 监测站 ECEF Y，单位米
     */
    @Builder.Default
    private Double Y = 0D;

    /**
     * 监测站 ECEF Z，单位米
     */
    @Builder.Default
    private Double Z = 0D;

    /**
     * 监测站 WGS84 大地纬度，单位度
     */
    @Builder.Default
    private Double B = 0D;

    /**
     * 监测站 WGS84 大地经度，单位度
     */
    @Builder.Default
    private Double L = 0D;

    /**
     * 监测站 WGS84 椭球高，单位米；不是正常高
     */
    @Builder.Default
    private Double H = 0D;

    /**
     * 解算状态
     */
    private String solStatus;

    /**
     * 卫星数量
     */
    private Integer satNum;

    /**
     * 监测站采样频率
     */
    private Integer roverSample;

    /**
     * 基准站采样率
     */
    private Integer baseSample;

    /**
     * 文件状态
     */
    private int fileStatus;

    /**
     * 文件状态说明
     */
    private String fileStatusDesc;

    /**
     * 星历状态
     */
    private int navStatus;

    /**
     * 原生结果中的星历数量；不含此字段的旧格式为 0
     */
    private int navNum;

    /**
     * 监测站历元数量
     */
    private int roverObsNum;

    /**
     * 基准站历元数量
     */
    private int baseObsNum;

    /**
     * 星历状态说明
     */
    private String navStatusDesc;

    /**
     * 解算耗时
     */
    private String offTime;

    /**
     * 解算模式： 0-纯实时 1-实时任务 2-重算任务
     */
    private int rtMode;

    /**
     * 是否vrs
     */
    @Builder.Default
    private int vrs = 0;

    /**
     * 任务类型
     */
    private int taskType;

    /**
     * 滤波时长(单位s)
     */
    private Integer filterPeriod;

    /**
     * 任务间隔(单位s)
     */
    private Integer processInterval;

    /**
     * 固定率
     */
    private Double fixedRate;

    private Double dposmax;

    private Double dposavg;

    private Double dposstd;

    /**
     * 0-历元解 1-单一解
     */
    private int outMode;

    /**
     * 错误信息
     */
    private String errMsg;

    /**
     * 测站历元完整率
     */
    private Double roverEpochRate;

    /**
     * 基准站历元完整率
     */
    private Double baseEpochRate;

    /**
     * 解算质量
     */
    private int solutionType;

    /**
     * 卫星系统
     */
    private String navSys;

    /**
     * 额外信息
     */
    private String extra;

}
