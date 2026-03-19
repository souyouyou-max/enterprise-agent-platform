package com.enterprise.agent.app.config;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * 启动后探测 Nacos 配置是否真实可读。
 * 只打关键信息（server-addr / namespace / group / dataId / content-length），避免泄露配置内容。
 */
@Slf4j
@Configuration
@ConditionalOnBean(NacosConfigProperties.class)
public class NacosConfigProbe implements ApplicationListener<ApplicationReadyEvent> {

    private final NacosConfigProperties nacosConfigProperties;

    public NacosConfigProbe(NacosConfigProperties nacosConfigProperties) {
        this.nacosConfigProperties = nacosConfigProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String serverAddr = nacosConfigProperties.getServerAddr();
        String namespace = nacosConfigProperties.getNamespace();
        String group = nacosConfigProperties.getGroup();

        log.info("[NacosProbe] serverAddr={}, namespace={}, group={}", serverAddr, blankToEmpty(namespace), blankToEmpty(group));
        try {
            ConfigService configService = NacosFactory.createConfigService(toNacosProperties(nacosConfigProperties));
            probeOne(configService, "common.yml", group);
            probeOne(configService, "eap-app.yml", group);
        } catch (Exception e) {
            log.warn("[NacosProbe] create ConfigService failed: {}", e.toString());
        }
    }

    private void probeOne(ConfigService configService, String dataId, String group) {
        try {
            String content = configService.getConfig(dataId, group, 3000);
            int len = content == null ? -1 : content.length();
            log.info("[NacosProbe] getConfig dataId={}, group={} -> length={}", dataId, group, len);
        } catch (Exception e) {
            log.warn("[NacosProbe] getConfig dataId={}, group={} failed: {}", dataId, group, e.toString());
        }
    }

    private static String blankToEmpty(String s) {
        return (s == null || s.isBlank()) ? "" : s;
    }

    private static Properties toNacosProperties(NacosConfigProperties props) {
        Properties p = new Properties();
        if (props.getServerAddr() != null && !props.getServerAddr().isBlank()) {
            p.setProperty(PropertyKeyConst.SERVER_ADDR, props.getServerAddr());
        }
        if (props.getNamespace() != null && !props.getNamespace().isBlank()) {
            p.setProperty(PropertyKeyConst.NAMESPACE, props.getNamespace());
        }
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            p.setProperty(PropertyKeyConst.USERNAME, props.getUsername());
        }
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            p.setProperty(PropertyKeyConst.PASSWORD, props.getPassword());
        }
        return p;
    }
}

