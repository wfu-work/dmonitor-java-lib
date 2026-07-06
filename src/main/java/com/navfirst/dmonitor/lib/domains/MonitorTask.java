package com.navfirst.dmonitor.lib.domains;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建：馥溪凝
 * 日期：2022/04/09 15:23
 * 描述：com.navfirst.dmonitor.lib.domains
 * 准实时模式，不传入广播星历文件，监测站名0， 基准站名 1，解算时长1小时，解算间隔5min，动态模式
 * file mode       : 1-rtcm3; 2-rinex
 */
/* time start */
/* time end */
/* sample */
/* ref station is VRS mode  : 0-false; 1:true */
/* rover file name */
/* base file name */
/* files directory */
/* filename extension */
/* broadcast file full name */
/* configure file full name(static.conf)

 * 1@1@@@3600@300@15@2@0@1@/data/rtcm/@
 * 3@1@2023/04/05 16:30:00@2023/04/05 20:30:00@15@0@X213PCCA1732@X213PCCA1729@/data/rtcm/@@@/data/static_platform.conf
 * rtMode@fileMode@timeStart@timeEndt@sample@vrs@roverName@baseName@dir@@brdcFile@conf
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 4168483660656762466L;

    /**
     * 解算状态，0代表SPP，2代表动态，3代表静态
     */
    @Builder.Default
    private int rtMode = 3;

    /**
     * 开始时间
     */
    private String timeStart;

    /**
     * 结束时间
     */
    private String timeEnd;

    /**
     * 滤波时长(单位s)
     */
    private Integer filterPeriod;

    /**
     * 任务间隔(单位s)
     */
    private Integer processInterval;

    /**
     * 采样频率
     */
    private Integer sample;

    /**
     * 是否vrs
     */
    @Builder.Default
    private int vrs = 0;

    /**
     * 监测站
     */
    private String roverName;

    /**
     * 基准站
     */
    private String baseName;

    /**
     * 星历文件流
     */
    private byte[] brdcBytes;

    /**
     * 基站文件流
     */
    private byte[] baseBytes;

    /**
     * 测站文件流
     */
    private byte[] roverBytes;

    /**
     * 输出结果：0-历元解 1-单一解
     */
    private int outMode;

    /**
     * 输出解算结果文件
     */
    private String outFile;

    /**
     * 固定率
     */
    private Double minFixedRate;

    /**
     * 任务 0-准实时 1-重算
     */
    private int taskType;

    /**
     * 卫星系统
     */
    private String navSys;

    /**
     * 北斗频段 0：B1I+B3I 1：B1C+B2A
     */
    private int bds;

    /**
     * 额外信息
     */
    private String extra;

    /**
     * 基站x
     */
    private Double baseX;

    /**
     * 基站y
     */
    private Double baseY;

    /**
     * 基站z
     */
    private Double baseZ;

}
