package com.paytech.client.master.service.processor;

import com.paytech.client.master.domain.enums.ClientType;
import com.paytech.client.master.domain.onboarding.ClientFormOptions;
import com.paytech.client.master.domainservice.service.ClientFormCatalogService;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.service.processor.request.GetClientFormOptionsRequest;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * C03 表单目录：按客户类型返回受控组织类型、业务模式与证件类型。
 * <li>当前会话在目录读取前后核验，外部查询不进入本地数据库事务；只读用例不分配申请、Party、身份键或任何业务状态。</li>
 */
@AllArgsConstructor
public class GetClientFormOptionsProcessor extends AbstractProcessor<GetClientFormOptionsRequest, ClientFormOptions> {
    private final ClientDraftAccountClient accounts;
    private final ClientFormCatalogService catalog;

    @Override
    protected ClientFormOptions processRequest(GetClientFormOptionsRequest request) {
        ClientType clientType = clientType(request.getClientType());
        accounts.authorize(request.getAccountId(), request.getSessionId());
        var result = catalog.options(clientType);
        accounts.authorize(request.getAccountId(), request.getSessionId());
        return result;
    }

    /** 客户类型按名称解析；未知或为空返回参数错误。 */
    ClientType clientType(String type) {
        try {
            return ClientType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new InvalidParameterException("客户类型无效");
        }
    }
}
