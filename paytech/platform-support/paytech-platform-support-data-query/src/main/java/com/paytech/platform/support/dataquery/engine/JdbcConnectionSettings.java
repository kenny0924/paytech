package com.paytech.platform.support.dataquery.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;

/**
 * 按字段拼 JDBC URL 与驱动参数；不接受原始 JDBC URL。
 *
 * <p>参数写入顺序：白名单参数 → 默认连接超时 → 安全参数 → 方言参数，后写覆盖先写，用户不能覆盖安全与方言参数。</p>
 */
public final class JdbcConnectionSettings {

    /** 安全参数：禁止多语句、本地文件读取与反序列化。 */
    static final Map<String, String> FORCED = Map.of("allowMultiQueries", "false", "allowLoadLocalInfile", "false",
        "allowUrlInLocalInfile", "false", "autoDeserialize", "false");

    /** 未设置 connectTimeout 时的连接超时毫秒。 */
    static final String DEFAULT_CONNECT_TIMEOUT_MILLIS = "3000";

    private JdbcConnectionSettings() {
    }

    /**
     * JDBC URL：只含主机、端口与可选默认库；参数全部经驱动属性传递。
     *
     * @param config 连接参数，主机与库名已按格式校验
     * @return URL
     */
    public static String url(DataSourceConnectionConfig config) {
        return "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/"
            + (config.getDatabaseName() == null ? "" : config.getDatabaseName());
    }

    /**
     * 驱动参数，不含账号与密码。
     *
     * @param config 连接参数
     * @return 有序参数
     */
    public static Map<String, String> driverProperties(DataSourceConnectionConfig config) {
        Map<String, String> properties = new LinkedHashMap<>(config.getConnectionParams());
        properties.putIfAbsent("connectTimeout", DEFAULT_CONNECT_TIMEOUT_MILLIS);
        properties.putAll(FORCED);
        properties.putAll(SqlDialect.forEngine(config.getEngineType()).sessionProperties());
        return properties;
    }

    /**
     * 单连接检查使用的完整属性，含账号与密码。
     *
     * @param config 连接参数
     * @param password 密码原文
     * @return 属性；调用方不得记录
     */
    static Properties connectionProperties(DataSourceConnectionConfig config, String password) {
        Properties properties = new Properties();
        properties.putAll(driverProperties(config));
        properties.setProperty("user", config.getUsername());
        properties.setProperty("password", password);
        return properties;
    }
}
