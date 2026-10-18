package com.paytech.instance.manifest;

/**
 * 实例中一个服务的运行参数。
 *
 * @param name 服务名
 * @param port 业务端口；管理端口为 port + 1000
 * @param snowflakeWorkerId Snowflake worker id
 * @param database 数据库名；服务没有数据库时为 null
 * @author Claude
 */
public record ServiceRuntime(String name, int port, int snowflakeWorkerId, String database) {

    /**
     * 管理端口。
     *
     * @return 业务端口 + 1000
     */
    public int managementPort() {
        return port + 1000;
    }
}
