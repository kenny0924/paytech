package com.paytech.client.master.domainservice.service;
import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ClientFormOptions;
/** 本地受控目录查询，不访问外部服务。 */
public interface ClientFormCatalogService {
    /** 按客户类型读取通用目录；没有配置时显式不可用。 */
    ClientFormOptions options(ClientType type);
}
