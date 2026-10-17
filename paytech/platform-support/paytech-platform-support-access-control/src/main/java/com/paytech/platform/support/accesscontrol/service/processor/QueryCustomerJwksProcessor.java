package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.authentication.PublicVerificationKey;
import com.paytech.platform.support.accesscontrol.domainservice.service.CustomerJwksService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryCustomerJwksRequest;

import lombok.AllArgsConstructor;

/** CUSTOMER JWKS 查询入口，统一经过 Processor 模板的调用方事务与请求检查。 */
@Component
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
@AllArgsConstructor
public class QueryCustomerJwksProcessor
    extends AbstractProcessor<QueryCustomerJwksRequest, List<PublicVerificationKey>> {

    private final CustomerJwksService jwksService;

    /** 复用查询与签发共同依赖的本地公钥发布规则，返回完整且已校验的集合。 */
    @Override
    protected List<PublicVerificationKey> processRequest(QueryCustomerJwksRequest request) {
        return jwksService.queryCurrentJwks();
    }
}
