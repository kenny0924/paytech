package com.paytech.platform.support.accesscontrol.domainservice.service;

/** 按 CCT Client ID 解析 Platform CLIENT 租户；供其他模块使用，不返回持久化对象。 */
public interface ClientTenantQueryService {

    /**
     * 读取 Client 对应的 CLIENT 租户
     *
     * @param clientId CCT Client ID
     * @return 租户 ID；Client 未开通平台访问时为空
     */
    Long findClientTenantId(long clientId);
}
