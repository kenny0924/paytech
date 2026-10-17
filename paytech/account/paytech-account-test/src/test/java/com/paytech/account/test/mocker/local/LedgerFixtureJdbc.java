package com.paytech.account.test.mocker.local;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 共库过渡期的测试夹具写连接
 * <li>account-app 的 account_app 在 mor_ledger 只有 SELECT；夹具与清理经 ledger 的测试账号写入 t_ledger_book / t_account。</li>
 * <li>不注册为 DataSource Bean，避免影响应用的 DataSource 自动装配；第 11b 步 account 拥有自有库后删除。</li>
 */
@TestComponent
public class LedgerFixtureJdbc {
    private final JdbcTemplate jdbc;

    public LedgerFixtureJdbc(@Value("${account.test.fixture-datasource.url}") String url,
        @Value("${account.test.fixture-datasource.username}") String username,
        @Value("${account.test.fixture-datasource.password}") String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 夹具写连接。 */
    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
