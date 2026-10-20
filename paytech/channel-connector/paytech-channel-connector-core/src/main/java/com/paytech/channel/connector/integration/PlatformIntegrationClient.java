package com.paytech.channel.connector.integration;

/** Platform 接入方存在性查询的本地出站端口；只在数据库事务外调用，不自动重试。 */
public interface PlatformIntegrationClient {

    /**
     * 查询 Platform 是否登记了同编码接入方
     *
     * @param integrationCode 接入方编码；支付渠道以渠道编码作为接入方编码
     * @return 是否存在
     * @throws com.paytech.foundation.common.error.ErrorException Platform 不可用、超时或响应不符合契约
     */
    boolean exists(String integrationCode);
}
