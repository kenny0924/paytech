package com.paytech.data.app.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

/**
 * Doris 连通性：经 FE 执行 {@code SELECT 1}，失败为 DOWN；不返回连接串或凭据。
 *
 * @author Claude
 */
public class DorisHealthIndicator extends AbstractHealthIndicator {

    private static final String PROBE_QUERY = "SELECT 1";

    private final DataSource dataSource;

    private final Duration timeout;

    /**
     * 创建指示器。
     *
     * @param dataSource Doris 连接池
     * @param timeout 查询超时
     */
    public DorisHealthIndicator(DataSource dataSource, Duration timeout) {
        super("Doris 连通性检查失败");
        this.dataSource = dataSource;
        this.timeout = timeout;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(Math.max(1, (int) timeout.toSeconds()));
            try (ResultSet result = statement.executeQuery(PROBE_QUERY)) {
                if (result.next() && result.getInt(1) == 1) {
                    builder.up();
                    return;
                }
            }
        }
        builder.down().withDetail("reason", "探测查询结果异常");
    }
}
