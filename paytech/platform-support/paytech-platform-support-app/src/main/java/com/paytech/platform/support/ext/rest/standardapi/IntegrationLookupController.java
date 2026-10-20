package com.paytech.platform.support.ext.rest.standardapi;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.standardapi.IntegrationLookupApi;
import com.paytech.platform.support.api.standardapi.response.IntegrationExistenceResponseBody;
import com.paytech.platform.support.integration.domainservice.service.IntegrationLookupService;

import lombok.AllArgsConstructor;

/**
 * 接入方存在性内部查询的协议适配：编码格式由契约注解经 MVC 方法校验，查询委托 integration 模块的本地 Service。
 *
 * <p>
 * 与标准 API 执行同属 /standard-apis/v1 前缀，不需要运营会话；internal-auth 非 OFF 时由 Foundation 入站过滤器校验内部 Token。
 */
@RestController
@AllArgsConstructor
public class IntegrationLookupController implements IntegrationLookupApi {

    private final IntegrationLookupService lookupService;

    /** 原样回显编码并返回是否存在。 */
    @Override
    public IntegrationExistenceResponseBody integrationExistence(String integrationCode) {
        return new IntegrationExistenceResponseBody(integrationCode, lookupService.exists(integrationCode));
    }
}
