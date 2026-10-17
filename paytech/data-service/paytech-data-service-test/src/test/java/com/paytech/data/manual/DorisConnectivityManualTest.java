package com.paytech.data.manual;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.data.DataServiceApplication;
import com.paytech.data.app.health.DorisHealthIndicator;
import com.paytech.data.test.checker.HealthResponseChecker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

/**
 * 真实 Doris 联调：需要 {@code analytics} Profile 在跑且演示管道已同步 {@code t_currency_spec}。
 *
 * <p>
 * 运行前在同一终端导出只读账号密码（不打印）：
 * {@code set -a; source local-environment/.env.local; set +a}，再执行
 * {@code ./mvnw -B -Pmanual-test -pl data-service/paytech-data-service-test -am verify}。
 *
 * @see DorisHealthIndicator#health()
 * @author Claude
 */
@ActiveProfiles("manual")
@SpringBootTest(classes = DataServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DorisConnectivityManualTest {

    private static final String DEMO_COUNT_QUERY = "SELECT COUNT(*) FROM t_currency_spec";

    @Autowired
    private DataSource dorisDataSource;

    @LocalManagementPort
    private int managementPort;

    @Test
    void test_demoTableSynchronized() throws Exception {
        try (Connection connection = dorisDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(DEMO_COUNT_QUERY)) {
            assertTrue(result.next(), "行数查询无结果");
            long rows = result.getLong(1);
            assertTrue(rows > 0, "Doris 演示表无数据，先按 paytech/data-service/README.md 提交管道: " + rows);
        }
        HealthResponseChecker.of(RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + managementPort).build()
                .get().uri("/actuator/health").exchange().expectBody().returnResult())
            .status("UP")
            .component("doris", "UP");
    }
}
