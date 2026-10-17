package com.paytech.platform.support.ext.rest.authentication;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.authentication.PublicVerificationKey;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryCustomerJwksRequest;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryOperationsJwksRequest;
import com.paytech.platform.support.api.authentication.response.JsonWebKeyResponseBody;
import com.paytech.platform.support.api.authentication.response.JsonWebKeySetResponseBody;

/** 将已校验的公开验签材料投影为 JWKS HTTP 合同。 */
@Component
public class JwksConverter {

    /** 将无参数的 Operations HTTP 查询适配为模块内请求。 */
    public QueryOperationsJwksRequest operationsRequest() {
        return new QueryOperationsJwksRequest();
    }

    /** 将无参数的 CUSTOMER HTTP 查询适配为模块内请求。 */
    public QueryCustomerJwksRequest customerRequest() {
        return new QueryCustomerJwksRequest();
    }

    /** 只转换公开字段，保持 Owner 给出的密钥顺序。 */
    public JsonWebKeySetResponseBody response(List<PublicVerificationKey> keys) {
        return new JsonWebKeySetResponseBody(keys.stream().map(this::keyBody).toList());
    }

    /** key_ops 的协议名称由响应模型固定。 */
    JsonWebKeyResponseBody keyBody(PublicVerificationKey key) {
        return new JsonWebKeyResponseBody(key.kty(), key.kid(), key.use(), key.keyOperations(), key.alg(), key.n(), key.e());
    }
}
