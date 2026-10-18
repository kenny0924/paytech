package com.paytech.platform.support.accesscontrol.domain.apimanagement;

import java.time.Instant;

import com.paytech.platform.support.accesscontrol.domain.enums.ApiCredentialStatus;

/** Client API 管理的模块业务合同。 */
public final class ClientApiManagementModels {
    /** 只承载类型或静态转换，禁止创建无业务意义的实例。 */
    private ClientApiManagementModels() {}

    /**
     * 目录当前事实，不具有生命周期状态。
     * 
     * @param apiScopeId
     *            目录 Snowflake 标识，与授权明细主键不同。
     * @param scopeCode
     *            小写 ASCII 精确目录编码。
     * @param displayName
     *            可修改的展示名称。
     * @param description
     *            可选说明，为 null 表示未填写。
     * @param version
     *            目录行并发版本。
     * @param createTime
     *            UTC 微秒创建时间。
     * @param updateTime
     *            UTC 微秒更新时间。
     */
    public record Scope(
        Long apiScopeId,
        String scopeCode,
        String displayName,
        String description,
        int version,
        Instant createTime,
        Instant updateTime) {}

    /**
     * 分页及总数共享的 AND 条件。
     * 
     * @param clientId
     *            Client 精确过滤，可空。
     * @param clientName
     *            转义后的包含匹配，可空。
     * @param credentialId
     *            凭证精确过滤，可空。
     * @param keyPrefix
     *            前缀精确过滤，可空。
     * @param status
     *            派生状态过滤，可空。
     * @param scopeCode
     *            任意状态的授权记录过滤，可空。
     */
    public record CredentialFilter(
        Long clientId,
        String clientName,
        Long credentialId,
        String keyPrefix,
        ApiCredentialStatus status,
        String scopeCode) {}

    /**
     * 最小稳定结果，由具体入口决定响应类型。
     * 
     * @param objectId
     *            当前命令对象。
     * @param objectCode
     *            目录为 code；凭证为十进制 ID。
     * @param version
     *            当次结果版本。
     */
    public record Mutation(
        long objectId,
        String objectCode,
        int version) {}
}
