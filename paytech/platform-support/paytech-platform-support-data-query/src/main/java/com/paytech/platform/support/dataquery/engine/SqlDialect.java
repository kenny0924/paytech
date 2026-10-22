package com.paytech.platform.support.dataquery.engine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;

/**
 * JDBC 方言差异：禁止引用的系统库、只读会话的驱动参数与账号权限检查；由 {@link DataSourceEngineType} 选定实现。
 *
 * <p>information_schema 各方言都允许，用于查看元数据。</p>
 */
public interface SqlDialect {

    /**
     * 本方言对应的引擎类型。
     *
     * @return 引擎类型
     */
    DataSourceEngineType engineType();

    /**
     * 是否为禁止引用的系统库，不区分大小写。
     *
     * @param schema 库名，已去掉引号
     * @return true 表示禁止引用
     */
    boolean isSystemSchema(String schema);

    /**
     * 本方言固定的驱动参数，在白名单参数与安全参数之后写入，用户不能覆盖。
     *
     * @return 驱动参数
     */
    Map<String, String> sessionProperties();

    /**
     * 在已建立的单个连接上检查账号权限，任何一项不通过都给出具体原因。
     *
     * @param connection 连接，调用方负责关闭
     * @return 授权原文与不通过原因；connected 为 true
     * @throws SQLException 查询授权失败
     */
    ConnectionCheckResult inspectGrants(Connection connection) throws SQLException;

    /**
     * 引擎类型对应的方言。
     *
     * @param engineType 引擎类型
     * @return 方言
     */
    static SqlDialect forEngine(DataSourceEngineType engineType) {
        return engineType == DataSourceEngineType.DORIS ? DorisDialect.INSTANCE : MysqlDialect.INSTANCE;
    }
}
