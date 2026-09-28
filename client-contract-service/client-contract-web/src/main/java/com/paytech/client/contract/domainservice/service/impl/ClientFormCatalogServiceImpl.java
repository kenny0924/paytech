package com.paytech.client.contract.domainservice.service.impl;
import java.util.List;
import lombok.AllArgsConstructor;
import com.paytech.client.contract.api.enums.ClientFormCatalogStatus;
import com.paytech.client.contract.configuration.ClientFormCatalogProperties;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.client.contract.domain.onboarding.ClientFormOptions;
import com.paytech.client.contract.domain.onboarding.ClientFormOptions.*;
import com.paytech.client.contract.domainservice.service.ClientFormCatalogService;
/** 明确筛选组织类型，不把居住国当成证件签发机构。 */
@AllArgsConstructor
public class ClientFormCatalogServiceImpl implements ClientFormCatalogService {
    private final ClientFormCatalogProperties catalog;
    /** 配置不可变，整个投影来自同一启动快照，不需要数据库事务。 */
    @Override public ClientFormOptions options(ClientType type,String country) {
        var organizations=type==ClientType.ORGANIZATION ? catalog.organizationTypes().stream()
            .filter(o -> o.countryCode().equals(country)).map(o -> new Option(o.code(),o.label())).toList() : List.<Option>of();
        var models=catalog.businessModels().stream().filter(o -> o.clientTypes().contains(type))
            .map(o -> new Option(o.code(),o.label())).toList();
        var identifiers=catalog.identifierTypes().stream().filter(o -> o.clientTypes().contains(type))
            .map(o -> new Identifier(o.code(),o.label(),o.issuers().stream()
                .map(i -> new Issuer(i.scopeKey(),i.label(),i.issuingCountry())).toList())).toList();
        var status=ClientFormCatalogStatus.AVAILABLE;
        if(models.isEmpty() || identifiers.isEmpty()) { status=ClientFormCatalogStatus.UNAVAILABLE; }
        else if(type==ClientType.ORGANIZATION && country==null) { status=ClientFormCatalogStatus.COUNTRY_REQUIRED; }
        else if(type==ClientType.ORGANIZATION && organizations.isEmpty()) { status=ClientFormCatalogStatus.UNAVAILABLE; }
        return new ClientFormOptions(status,organizations,models,identifiers);
    }
}
