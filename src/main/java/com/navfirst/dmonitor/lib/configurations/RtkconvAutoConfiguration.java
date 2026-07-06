package com.navfirst.dmonitor.lib.configurations;

import com.navfirst.dmonitor.lib.services.MonitorDataService;
import com.navfirst.dmonitor.lib.services.MonitorService;
import com.navfirst.dmonitor.lib.services.impl.MonitorDataServiceImpl;
import com.navfirst.dmonitor.lib.services.impl.MonitorServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 创建：馥溪凝
 * 日期：2022/08/23 12:14
 * 描述：com.navfirst.dmonitor.lib.configurations
 */
@Configuration(proxyBeanMethods = false)
@AutoConfiguration
@ComponentScan(basePackages = "com.navfirst.dmonitor.lib")
public class RtkconvAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MonitorDataService monitorDataService() {
        return new MonitorDataServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public MonitorService monitorService(MonitorDataService monitorDataService) {
        return new MonitorServiceImpl(monitorDataService);
    }

}
