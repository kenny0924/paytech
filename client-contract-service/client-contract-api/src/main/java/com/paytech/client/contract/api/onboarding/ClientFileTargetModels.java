package com.paytech.client.contract.api.onboarding;

/** 文件 Owner 所需的最小 CCT 授权投影，不包含客户资料或存储授权。 */
public final class ClientFileTargetModels {

    /** 禁止实例化协议模型容器。 */
    private ClientFileTargetModels() {}

    /**
     * 原申请和准确主体的当前授权事实；所有 ID 为无前导零的正整数十进制字符串。
     *
     * @param applicationId 原申请标识，不自动替换为账号最新申请
     * @param clientId 原申请所属 Client，供 Platform 核对 Tenant Owner
     * @param subjectPartyId 主主体或本申请有权读取的相关人员
     * @param clientType Client 类型代码 INDIVIDUAL 或 ORGANIZATION
     * @param canUpload 当前申请可编辑且主体仍处于当前材料范围时为 true
     * @param canRead 已通过原账号、申请和主体归属校验时为 true
     */
    public record FileTargetResponseBody(String applicationId, String clientId, String subjectPartyId,
                                         String clientType, boolean canUpload, boolean canRead) {}
}
