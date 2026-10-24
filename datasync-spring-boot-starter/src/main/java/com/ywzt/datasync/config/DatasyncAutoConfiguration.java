package com.ywzt.datasync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywzt.datasync.core.DataSyncExecutor;
import com.ywzt.datasync.core.DatasyncManager;
import com.ywzt.datasync.core.DatasyncScanner;
import com.ywzt.datasync.ds.SyncDbLinkResolver;
import com.ywzt.datasync.ds.SyncDataSourceManager;
import com.ywzt.datasync.repo.SyncTaskRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableConfigurationProperties(DatasyncProperties.class)
@ConditionalOnProperty(prefix = "datasync", name = "enabled", matchIfMissing = true)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class DatasyncAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SyncTaskRepository syncTaskRepository(ObjectProvider<DataSource> dataSourceProvider,
                                                 DatasyncProperties properties) {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            throw new IllegalStateException("datasync 需要宿主项目存在可用的 DataSource");
        }
        SyncTaskRepository repository = new SyncTaskRepository(new JdbcTemplate(dataSource), properties.getTableName());
        if (properties.isInitTable()) {
            repository.ensureTable();
        }
        return repository;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public SyncDataSourceManager syncDataSourceManager(ObjectProvider<DataSource> dataSourceProvider,
                                                       DatasyncProperties properties,
                                                       ObjectProvider<SyncDbLinkResolver> resolverProvider) {
        return new SyncDataSourceManager(dataSourceProvider.getIfAvailable(),
                properties.getDatasources(), resolverProvider.getIfAvailable());
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "datasyncTriggerPool")
    public ThreadPoolExecutor datasyncTriggerPool(DatasyncProperties properties) {
        AtomicInteger seq = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                properties.getAsync().getCorePoolSize(), properties.getAsync().getMaxPoolSize(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getAsync().getQueueCapacity()),
                r -> {
                    Thread t = new Thread(r, "datasync-worker-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSyncExecutor dataSyncExecutor(SyncTaskRepository repository, SyncDataSourceManager dataSourceManager,
                                             ObjectProvider<ObjectMapper> objectMapperProvider,
                                             ApplicationContext context, ThreadPoolExecutor datasyncTriggerPool) {
        DataSyncExecutor executor = new DataSyncExecutor(repository, dataSourceManager,
                objectMapperProvider.getIfAvailable(ObjectMapper::new), context);
        executor.setTriggerExecutor(datasyncTriggerPool);
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public DatasyncManager datasyncManager(SyncTaskRepository repository, DataSyncExecutor executor) {
        return new DatasyncManager(repository, executor);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "datasync.scheduler", name = "enabled", matchIfMissing = true)
    public DatasyncScanner datasyncScanner(SyncTaskRepository repository, DataSyncExecutor executor,
                                           DatasyncProperties properties) {
        return new DatasyncScanner(repository, executor, properties.getScheduler().getIntervalMs());
    }
}
