package com.paytech.platform.support.dataquery.integration;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryLimitsProperties;
import com.paytech.platform.support.dataquery.core.dal.dataobject.DataSource;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domainservice.convert.DataQueryDetailConverter;
import com.paytech.platform.support.dataquery.domainservice.repository.DataSourceRepository;
import com.paytech.platform.support.dataquery.engine.DataSourceHandle;
import com.paytech.platform.support.dataquery.engine.JdbcConnectionSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 各 ACTIVE 数据源的 Hikari 连接池；按数据源版本增量重建，多实例经定时刷新秒级收敛（technique 5.3）。
 *
 * <li>池参数：minimumIdle=0、readOnly=true、connectionTimeout=3s、idleTimeout=10m，maximumPoolSize 取数据源配置。</li>
 * <li>新建或版本变化：读取 Vault 后新建池并原子替换，旧池退役；变为 INACTIVE、DELETED 或不存在：移出并退役。</li>
 * <li>退役池不再借出，进行中查询归零或等待 max-timeout 加 10 秒后关闭。</li>
 * <li>单个数据源刷新失败（如 Vault 不可用）保留旧池并累计失败指标，下一轮重试。</li>
 * <li>Vault 与连接池构建只在数据库事务外进行；池不注册为 Spring Bean，被查库不可用不影响健康检查。</li>
 */
@Component
public class DataSourcePoolRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DataSourcePoolRegistry.class);

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration RETIRE_GRACE = Duration.ofSeconds(10);

    private final Map<Long, PoolEntry> pools = new ConcurrentHashMap<>();

    private final Map<Long, Object> buildLocks = new ConcurrentHashMap<>();

    private final List<PoolEntry> retired = new CopyOnWriteArrayList<>();

    private final MeterRegistry meters;

    private final DataSourcePasswordVault vault;

    private final DataSourceRepository dataSources;

    private final DataQueryLimitsProperties limits;

    /**
     * 构造。
     *
     * @param meters 指标
     * @param vault 密码读取
     * @param dataSources 数据源仓储
     * @param limits 硬上限
     */
    public DataSourcePoolRegistry(MeterRegistry meters, DataSourcePasswordVault vault,
        DataSourceRepository dataSources, DataQueryLimitsProperties limits) {
        this.meters = meters;
        this.vault = vault;
        this.dataSources = dataSources;
        this.limits = limits;
    }

    /**
     * 借出数据源；本地池缺失或版本落后时先以该版本同步重建，再借出。
     *
     * @param source 调用方刚读取的 ACTIVE 数据源
     * @return 借出句柄，调用方必须关闭
     * @throws BizCheckFailException 数据源不是 ACTIVE
     */
    public DataSourceHandle lease(DataSourceDetail source) {
        if (!source.statusIs(DataSourceStatus.ACTIVE)) {
            throw new BizCheckFailException("数据源未启用: " + source.getDataSourceCode());
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            PoolEntry entry = pools.get(source.getDataSourceId());
            if (entry == null || entry.version < source.getVersion()) {
                entry = rebuild(source);
            }
            if (entry.acquire()) {
                return new Lease(entry);
            }
        }
        throw new BizCheckFailException("数据源连接池正在切换，请重试: " + source.getDataSourceCode());
    }

    /** 全量刷新：比较全部未删除数据源的版本与状态，再关闭可以关闭的退役池。 */
    public void refresh() {
        Map<Long, DataSource> states = dataSources.findRefreshStates().stream()
            .collect(Collectors.toMap(DataSource::getDataSourceId, state -> state));
        for (DataSource state : states.values()) {
            if (state.getStatus() == DataSourceStatus.ACTIVE) {
                refreshActive(state.getDataSourceId(), state.getVersion());
            }
        }
        Set<Long> local = Set.copyOf(pools.keySet());
        for (Long dataSourceId : local) {
            DataSource state = states.get(dataSourceId);
            if (state == null || state.getStatus() != DataSourceStatus.ACTIVE) {
                retire(dataSourceId);
            }
        }
        closeRetired();
    }

    /**
     * 单个数据源刷新：修改配置的实例在事务提交后立即调用。
     *
     * @param dataSourceId 数据源 ID
     */
    public void refresh(long dataSourceId) {
        DataSource row = dataSources.findById(dataSourceId);
        if (row == null || row.getStatus() != DataSourceStatus.ACTIVE) {
            retire(dataSourceId);
        } else {
            refreshActive(dataSourceId, row.getVersion());
        }
        closeRetired();
    }

    /**
     * 本地连接池的版本，供观测与测试。
     *
     * @param dataSourceId 数据源 ID
     * @return 版本；没有连接池时为 null
     */
    public Integer poolVersion(long dataSourceId) {
        PoolEntry entry = pools.get(dataSourceId);
        return entry == null ? null : entry.version;
    }

    /** 关闭全部连接池。 */
    @Override
    public void destroy() {
        pools.values().forEach(PoolEntry::close);
        pools.clear();
        retired.forEach(PoolEntry::close);
        retired.clear();
    }

    /**
     * 版本变化的 ACTIVE 数据源重建；失败保留旧池并记指标，不中断其他数据源的刷新。
     *
     * @param dataSourceId 数据源 ID
     * @param version 数据库中的版本
     */
    void refreshActive(long dataSourceId, int version) {
        PoolEntry entry = pools.get(dataSourceId);
        if (entry != null && entry.version >= version) {
            return;
        }
        try {
            DataSource row = dataSources.findById(dataSourceId);
            if (row != null && row.getStatus() == DataSourceStatus.ACTIVE) {
                rebuild(DataQueryDetailConverter.dataSource(row, 0, 0));
            }
        } catch (RuntimeException failure) {
            // 刷新失败只影响该数据源：保留旧池，下一轮重试；不记录密码或 Token。
            meters.counter("data.query.pool.refresh.failures", "dataSource", Long.toString(dataSourceId)).increment();
            log.warn("数据源连接池刷新失败，保留旧池，dataSourceId={}，异常类型={}", dataSourceId,
                failure.getClass().getSimpleName());
        }
    }

    /**
     * 以给定版本重建连接池并原子替换；同一数据源串行构建，已有更新版本时直接返回。
     *
     * @param source 数据源
     * @return 当前连接池
     */
    PoolEntry rebuild(DataSourceDetail source) {
        Object lock = buildLocks.computeIfAbsent(source.getDataSourceId(), id -> new Object());
        synchronized (lock) {
            PoolEntry current = pools.get(source.getDataSourceId());
            if (current != null && current.version >= source.getVersion()) {
                return current;
            }
            PoolEntry created = new PoolEntry(source.getVersion(), createPool(source, vault.reveal(
                source.getPasswordToken())));
            PoolEntry previous = pools.put(source.getDataSourceId(), created);
            if (previous != null) {
                retireEntry(previous);
            }
            log.info("数据源连接池已就绪，dataSourceId={}，version={}", source.getDataSourceId(), source.getVersion());
            return created;
        }
    }

    /**
     * 新建 Hikari 连接池；不在构建时建立连接。
     *
     * @param source 数据源
     * @param password 密码原文
     * @return 连接池
     */
    HikariDataSource createPool(DataSourceDetail source, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("data-query-" + source.getDataSourceCode() + "-v" + source.getVersion());
        config.setJdbcUrl(JdbcConnectionSettings.url(source.getConnection()));
        config.setUsername(source.getConnection().getUsername());
        config.setPassword(password);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(Math.min(source.getMaxPoolSize(), limits.getMaxPoolSize()));
        config.setConnectionTimeout(CONNECTION_TIMEOUT.toMillis());
        config.setIdleTimeout(IDLE_TIMEOUT.toMillis());
        config.setInitializationFailTimeout(-1);
        JdbcConnectionSettings.driverProperties(source.getConnection()).forEach(config::addDataSourceProperty);
        return new HikariDataSource(config);
    }

    /**
     * 移出并退役某数据源的连接池。
     *
     * @param dataSourceId 数据源 ID
     */
    void retire(long dataSourceId) {
        PoolEntry entry = pools.remove(dataSourceId);
        if (entry != null) {
            retireEntry(entry);
            log.info("数据源连接池已退役，dataSourceId={}，version={}", dataSourceId, entry.version);
        }
    }

    /** 标记退役；没有进行中查询时立即关闭，否则留待归还或宽限期满。 */
    void retireEntry(PoolEntry entry) {
        entry.retire(System.nanoTime());
        if (!entry.closeIfIdle()) {
            retired.add(entry);
        }
    }

    /** 关闭进行中查询已归零或宽限期满的退役池。 */
    void closeRetired() {
        long graceNanos = limits.getMaxTimeout().plus(RETIRE_GRACE).toNanos();
        for (PoolEntry entry : retired) {
            if (entry.closeIfIdle() || entry.closeIfExpired(System.nanoTime(), graceNanos)) {
                retired.remove(entry);
            }
        }
    }

    /** 一个版本的连接池与进行中查询计数。 */
    static final class PoolEntry {

        /** 数据源版本。 */
        final int version;

        /** 连接池。 */
        final HikariDataSource pool;

        private int inFlight;

        private boolean retiredFlag;

        private long retiredAtNanos;

        /**
         * 构造。
         *
         * @param version 版本
         * @param pool 连接池
         */
        PoolEntry(int version, HikariDataSource pool) {
            this.version = version;
            this.pool = pool;
        }

        /** 借出；已退役时拒绝。 */
        synchronized boolean acquire() {
            if (retiredFlag) {
                return false;
            }
            inFlight++;
            return true;
        }

        /** 归还；退役且归零时关闭。 */
        synchronized void release() {
            inFlight--;
            closeIfIdle();
        }

        /** 标记退役。 */
        synchronized void retire(long nowNanos) {
            retiredFlag = true;
            retiredAtNanos = nowNanos;
        }

        /** 退役且没有进行中查询时关闭。 */
        synchronized boolean closeIfIdle() {
            if (retiredFlag && inFlight <= 0) {
                close();
                return true;
            }
            return false;
        }

        /** 退役超过宽限期时强制关闭。 */
        synchronized boolean closeIfExpired(long nowNanos, long graceNanos) {
            if (retiredFlag && nowNanos - retiredAtNanos >= graceNanos) {
                close();
                return true;
            }
            return false;
        }

        /** 关闭连接池。 */
        synchronized void close() {
            if (!pool.isClosed()) {
                pool.close();
            }
        }
    }

    /** 借出句柄：关闭即归还。 */
    static final class Lease implements DataSourceHandle {

        private final PoolEntry entry;

        private boolean closed;

        /**
         * 构造。
         *
         * @param entry 已借出的连接池
         */
        Lease(PoolEntry entry) {
            this.entry = entry;
        }

        /** 借出时的版本。 */
        @Override
        public int version() {
            return entry.version;
        }

        /** 从连接池取连接。 */
        @Override
        public Connection connection() throws SQLException {
            return entry.pool.getConnection();
        }

        /** 只归还一次。 */
        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                entry.release();
            }
        }
    }
}
