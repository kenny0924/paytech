package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.clientteam.InvitationLinks.InvitationProof;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 被邀请人凭链接查看或接受邀请的内部请求；秘密只随本次调用存在。 */
@Getter
@AllArgsConstructor
public final class ClientInvitationRequest {
    /** 可信账号编号；公开预览为空。 */
    private final Long accountId;
    /** 可信会话编号；公开预览为空。 */
    private final Long sessionId;
    /** 挑战编号。 */
    private final String challengeNo;
    /** 链接秘密。 */
    private final String secret;
    /** 事务外核对的链接证明；由 Processor 填入。 */
    private final InvitationProof proof;

    /** 附上事务外核对结果。 */
    public ClientInvitationRequest withProof(InvitationProof value) {
        return new ClientInvitationRequest(accountId, sessionId, challengeNo, secret, value);
    }

    /** 不输出秘密或会话坐标。 */
    @Override public String toString() { return "ClientInvitationRequest[REDACTED]"; }
}
