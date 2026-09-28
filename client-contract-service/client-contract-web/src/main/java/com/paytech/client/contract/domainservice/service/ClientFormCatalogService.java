package com.paytech.client.contract.domainservice.service;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.client.contract.domain.onboarding.ClientFormOptions;
/** 本地受控目录查询，不访问外部服务。 */
public interface ClientFormCatalogService {
    /** 按客户类型和资料国家读取目录；没有配置时显式不可用。 */
    ClientFormOptions options(ClientType type,String country);
}
