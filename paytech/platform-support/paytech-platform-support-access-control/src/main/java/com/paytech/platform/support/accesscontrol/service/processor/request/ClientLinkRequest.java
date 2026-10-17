package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.authentication.ClientRegistrationModels.LinkProof;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 认证内部请求；敏感字段仅随本次调用存在。 */
@Getter
@AllArgsConstructor
public final class ClientLinkRequest {
    /** 挑战编号。 */
    private final String challengeNo;
    /** 链接秘密。 */
    private final String secret;
    /** 新密码；邮箱验证为空。 */
    private final String newPassword;
    /** 锁前计算的链接证明；入口为 null。 */
    private final LinkProof proof;

    /** 不允许诊断输出打印邮箱或凭据。 */
    @Override
    public String toString() {
        return "ClientLinkRequest[REDACTED]";
    }
}
