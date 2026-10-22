package com.paytech.platform.support.dataquery.engine;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 从连接池注册表借出的数据源：在归还前，即使数据源被停用或修改，连接池也不会关闭。
 */
public interface DataSourceHandle extends AutoCloseable {

    /**
     * 借出时的数据源版本。
     *
     * @return 版本
     */
    int version();

    /**
     * 从连接池取得只读连接，调用方负责关闭。
     *
     * @return 连接
     * @throws SQLException 取不到连接
     */
    Connection connection() throws SQLException;

    /** 归还，进行中计数减一；连接池已退役且计数归零时关闭。 */
    @Override
    void close();
}
