package com.paytech.migration.cli;

import com.paytech.migration.model.DatabaseCredentials;
import com.paytech.migration.service.RunnerConfigurationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从环境变量读取服务库连接信息；连接信息不进入实例清单（D22：运维平面不持实例数据库凭据）。
 *
 * <p>
 * 每个服务读取 {@code PAYTECH_DB_URL_<SERVICE>}、{@code PAYTECH_DB_USER_<SERVICE>}、{@code PAYTECH_DB_PASSWORD_<SERVICE>}，
 * 可选 {@code PAYTECH_DB_SCHEMA_<SERVICE>} 声明物理库名与清单 db 不同（如 {@code mor_platform_e2e}）；{@code <SERVICE>} 为服务名的
 * 大写下划线形式。
 *
 * @author Claude
 */
public class DatabaseEnvironment {

    private final Map<String, String> environment;

    /**
     * 绑定环境变量。
     *
     * @param environment 环境变量
     */
    public DatabaseEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    /**
     * 读取一组服务的连接信息；任一缺失时一次列出全部缺失的变量名。
     *
     * @param services 服务名
     * @return 服务名到连接信息
     * @throws RunnerConfigurationException 有变量缺失
     */
    public Map<String, DatabaseCredentials> credentials(List<String> services) {
        List<String> missing = new ArrayList<>();
        Map<String, DatabaseCredentials> credentials = new LinkedHashMap<>();
        for (String service : services) {
            String url = value("PAYTECH_DB_URL_", service, missing);
            String user = value("PAYTECH_DB_USER_", service, missing);
            String password = value("PAYTECH_DB_PASSWORD_", service, missing);
            credentials.put(service, new DatabaseCredentials(url, user, password));
        }
        if (!missing.isEmpty()) {
            throw new RunnerConfigurationException("缺少数据库连接环境变量: " + String.join(", ", missing));
        }
        return credentials;
    }

    /**
     * 服务的物理库名覆盖。
     *
     * @param service 服务名
     * @return {@code PAYTECH_DB_SCHEMA_<SERVICE>}；未设置为 null
     */
    public String schemaOverride(String service) {
        String value = environment.get(variable("PAYTECH_DB_SCHEMA_", service));
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 读取一个变量，缺失时记录变量名。
     *
     * @param prefix 变量前缀
     * @param service 服务名
     * @param missing 缺失收集
     * @return 值；缺失为 null
     */
    String value(String prefix, String service, List<String> missing) {
        String name = variable(prefix, service);
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            missing.add(name);
            return null;
        }
        return value;
    }

    /**
     * 服务的环境变量名。
     *
     * @param prefix 变量前缀
     * @param service 服务名，如 {@code channel-connector}
     * @return 如 {@code PAYTECH_DB_URL_CHANNEL_CONNECTOR}
     */
    public static String variable(String prefix, String service) {
        return prefix + service.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
