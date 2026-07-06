package com.navfirst.dmonitor.lib.services;

import com.navfirst.dmonitor.lib.domains.MonitorTask;

/**
 * 创建：馥溪凝
 * 日期：2022/04/09 15:26
 * 描述：com.navfirst.dmonitor.lib.services
 */
public interface MonitorService {

    void startMonitor(MonitorTask monitorTask, String license);

    void setHandlerData(HandlerDataInterface handlerDataInterface);

    String getVersion();

    String getUuid();

}
