package com.navfirst.dmonitor.lib.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExceptionEnum {

    SAVE_PATH_NOT_EMPTY(10001, "存储路径不能为空"),
    RAW_PATH_NOT_EMPTY(10002, "观测数据路径不能为空"),
    HOST_NOT_EMPTY(10003, "推送地址不能为空"),
    DEVICE_MAX_NUM(10004, "允许添加的设备已达最大"),
    ;

    private final int code;

    private final String msg;

}
