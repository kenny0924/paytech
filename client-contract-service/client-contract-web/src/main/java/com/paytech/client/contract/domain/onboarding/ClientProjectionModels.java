package com.paytech.client.contract.domain.onboarding;

/** CCT 生命周期事实与 Platform 投影结果，均不包含可编辑资料或 Vault 引用。 */
public final class ClientProjectionModels {
    private ClientProjectionModels() {}

    /** 原 Client 生命周期快照确定的投影；sourceVersion 不是表单版本。 */
    public record Source(long clientId, long applicantId, int sourceVersion, String status, String payloadHash) {}

    /** Platform 已持久化的准确来源位置；不会据此签发工作区会话。 */
    public record Position(long clientId, long applicantId, long tenantId, long memberId,
            int sourceVersion, String status, String payloadHash) {}
}
