package com.ywzt.datasync.config;

import com.ywzt.datasync.api.DatasyncController;
import com.ywzt.datasync.core.DatasyncManager;
import com.ywzt.datasync.core.DatasyncScanner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * /datasync 管理接口的自动配置。独立成类并用 @AutoConfigureAfter 排在
 * DatasyncAutoConfiguration 之后，@ConditionalOnBean(DatasyncManager) 才可用
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DatasyncAutoConfiguration.class)
@ConditionalOnWebApplication
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnBean(DatasyncManager.class)
public class DatasyncApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "datasync.api", name = "enabled", matchIfMissing = true)
    public DatasyncController datasyncController(DatasyncManager manager,
                                                 ObjectProvider<DatasyncScanner> scannerProvider) {
        return new DatasyncController(manager, scannerProvider);
    }
}
