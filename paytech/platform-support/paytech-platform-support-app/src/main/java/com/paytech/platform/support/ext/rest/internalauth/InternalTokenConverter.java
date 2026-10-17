package com.paytech.platform.support.ext.rest.internalauth;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.ECKey;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.platform.support.api.internalauth.request.InternalTokenIssueRequestBody;
import com.paytech.platform.support.api.internalauth.response.InternalJwksResponseBody;
import com.paytech.platform.support.api.internalauth.response.InternalPublicKey;
import com.paytech.platform.support.api.internalauth.response.InternalTokenIssueResponseBody;
import com.paytech.platform.support.service.processor.internalauth.request.IssueInternalTokenRequest;
import com.paytech.platform.support.service.processor.internalauth.response.IssueInternalTokenResponse;

/** 内部调用 Token 签发与 JWKS 的协议转换。 */
@Component
public class InternalTokenConverter {

    /** 无请求 ID 时写入审计的占位值（正常经 RequestIdFilter 总是存在）。 */
    private static final String UNKNOWN_REQUEST = "UNKNOWN";

    /** Header 调用方与 Body 合成内部请求，附带当前请求 ID 供审计。 */
    public IssueInternalTokenRequest convertRequest(String callerService, InternalTokenIssueRequestBody body) {
        String requestId = MDC.get(ApiProblemProfile.REQUEST_ID_MDC_KEY);
        return new IssueInternalTokenRequest(callerService, body.getTargetService(), body.getPurpose(),
            requestId == null ? UNKNOWN_REQUEST : requestId);
    }

    /** 过期时间按 UTC ISO-8601 秒精度输出。 */
    public InternalTokenIssueResponseBody convertResponse(IssueInternalTokenResponse response) {
        InternalTokenIssueResponseBody body = new InternalTokenIssueResponseBody();
        body.setToken(response.getToken());
        body.setExpiresAt(response.getExpiresAt().toString());
        return body;
    }

    /** 只输出公钥成员，保持签名公钥在前的顺序。 */
    public InternalJwksResponseBody convertJwks(List<ECKey> keys) {
        InternalJwksResponseBody body = new InternalJwksResponseBody();
        body.setKeys(keys.stream().map(InternalTokenConverter::publicKey).toList());
        return body;
    }

    /** P-256 公钥的标准 JWK 成员。 */
    static InternalPublicKey publicKey(ECKey key) {
        InternalPublicKey body = new InternalPublicKey();
        body.setKty(key.getKeyType().getValue());
        body.setKid(key.getKeyID());
        body.setUse(key.getKeyUse().identifier());
        body.setAlg(key.getAlgorithm().getName());
        body.setCrv(key.getCurve().getName());
        body.setX(key.getX().toString());
        body.setY(key.getY().toString());
        return body;
    }
}
