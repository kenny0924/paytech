package com.paytech.platform.support.dataquery.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.engine.QuerySource;

import lombok.Getter;

/** 建立连接所需的非秘密参数；密码另行传递，不进入本对象。 */
@Getter
public final class DataSourceConnectionConfig {

    /** connection_params 允许的五个驱动参数。 */
    public static final Set<String> ALLOWED_PARAMS = Set.of("sslMode", "connectTimeout", "socketTimeout",
        "connectionTimeZone", "characterEncoding");

    /** 引擎类型。 */
    private final DataSourceEngineType engineType;

    /** 主机名或 IP。 */
    private final String host;

    /** 端口。 */
    private final int port;

    /** 默认库；为 null 表示连接整个实例。 */
    private final String databaseName;

    /** 只读账号。 */
    private final String username;

    /** 白名单驱动参数，按键排序；没有时为空。 */
    private final Map<String, String> connectionParams;

    /**
     * 校验驱动参数白名单后构造。
     *
     * @param engineType 引擎类型
     * @param host 主机
     * @param port 端口
     * @param databaseName 默认库，空白视为 null
     * @param username 账号
     * @param connectionParams 驱动参数，可为 null
     * @throws InvalidParameterException 参数不在白名单或值不合法
     */
    public DataSourceConnectionConfig(DataSourceEngineType engineType, String host, int port, String databaseName,
        String username, Map<String, String> connectionParams) {
        this.engineType = engineType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName == null || databaseName.isBlank() ? null : databaseName;
        this.username = username;
        this.connectionParams = validParams(connectionParams);
    }

    /**
     * 只读校验需要的数据源事实。
     *
     * @return 引擎类型与默认库
     */
    public QuerySource querySource() {
        return new QuerySource(engineType, databaseName);
    }

    /**
     * 校验驱动参数：键在白名单内，值 1..64 个可见 ASCII 字符。
     *
     * @param params 驱动参数
     * @return 排序后的不可变副本
     */
    static Map<String, String> validParams(Map<String, String> params) {
        if (params == null) {
            return Map.of();
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!ALLOWED_PARAMS.contains(entry.getKey())) {
                throw new InvalidParameterException("驱动参数不允许: " + entry.getKey() + "，只允许 " + ALLOWED_PARAMS);
            }
            String value = entry.getValue();
            if (value == null || !value.matches("[!-~]{1,64}")) {
                throw new InvalidParameterException("驱动参数取值不合法: " + entry.getKey());
            }
            sorted.put(entry.getKey(), value);
        }
        return Collections.unmodifiableMap(sorted);
    }
}
