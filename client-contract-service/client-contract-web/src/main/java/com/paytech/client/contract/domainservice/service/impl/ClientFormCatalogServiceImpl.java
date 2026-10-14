package com.paytech.client.contract.domainservice.service.impl;
import java.util.List;
import lombok.AllArgsConstructor;
import com.paytech.client.contract.api.enums.ClientFormCatalogStatus;
import com.paytech.client.contract.configuration.ClientFormCatalogProperties;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.client.contract.domain.onboarding.ClientFormOptions;
import com.paytech.client.contract.domain.onboarding.ClientFormOptions.*;
import com.paytech.client.contract.domainservice.service.ClientFormCatalogService;
/** 通用目录只按客户类型筛选，不按国家区分。 */
@AllArgsConstructor
public class ClientFormCatalogServiceImpl implements ClientFormCatalogService {
    private final ClientFormCatalogProperties catalog;
    /** 配置不可变，整个投影来自同一启动快照，不需要数据库事务。 */
    @Override public ClientFormOptions options(ClientType type) {
        var organizations=type==ClientType.ORGANIZATION ? catalog.organizationTypes().stream()
            .map(o -> new Option(o.code(),o.label())).toList() : List.<Option>of();
        var models=catalog.businessModels().stream().filter(o -> o.clientTypes().contains(type))
            .map(o -> new Option(o.code(),o.label())).toList();
        var identifiers=catalog.identifierTypes().stream().filter(o -> o.clientTypes().contains(type))
            .map(o -> new Identifier(o.code(),o.label(),o.issuingCountryMode())).toList();
        var status=ClientFormCatalogStatus.AVAILABLE;
        if(models.isEmpty() || identifiers.isEmpty() || (type==ClientType.ORGANIZATION && organizations.isEmpty())) {
            status=ClientFormCatalogStatus.UNAVAILABLE;
        }
        return new ClientFormOptions(status,organizations,models,identifiers);
    }
}
