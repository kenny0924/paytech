package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domain.authentication.PublicVerificationKey;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsJwksService;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsJwksRequest;

import lombok.AllArgsConstructor;

/** Operations JWKS 查询入口，统一经过 Processor 模板的调用方事务与请求检查。 */
@Component
@AllArgsConstructor
public class QueryOperationsJwksProcessor
    extends AbstractProcessor<QueryOperationsJwksRequest, List<PublicVerificationKey>> {

    private final OperationsJwksService jwksService;

    /** 复用查询与签发共同依赖的本地公钥发布规则，返回完整且已校验的集合。 */
    @Override
    protected List<PublicVerificationKey> processRequest(QueryOperationsJwksRequest request) {
        return jwksService.queryCurrentJwks();
    }
}
