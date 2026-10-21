package com.paytech.channel.connector.protocol.domainservice.service;

/** 接入方存在性的本地只读查询；不开事务，不返回接入方配置。 */
public interface IntegrationLookupService {

    /**
     * 判断是否登记了该编码的接入方
     *
     * @param integrationCode
     *            接入方编码，格式已由入口校验
     * @return 存在返回 true；不区分接入方启停状态
     */
    boolean exists(String integrationCode);
}
