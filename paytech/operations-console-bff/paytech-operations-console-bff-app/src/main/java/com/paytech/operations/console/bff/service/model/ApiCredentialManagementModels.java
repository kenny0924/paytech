package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import jakarta.validation.constraints.*;

/** Operations 非秘密凭证管理合同。 */
public final class ApiCredentialManagementModels {
    /** 只承载类型或静态转换，禁止创建无业务意义的实例。 */
    private ApiCredentialManagementModels() {}

    /** 后台凭证摘要；此状态不能代替真实认证。
     * @param credentialId 凭证标识，正十进制字符串。
     * @param tenantId 所属 CLIENT Tenant 标识。
     * @param clientId Tenant 已投影的 Client 主档标识。
     * @param clientName Tenant 当前显示名称。
     * @param keyPrefix 非秘密查找前缀，不包含完整凭证。
     * @param storedStatus 持久化状态，不被派生有效期覆盖。
     * @param status 按查询时刻派生的 ACTIVE/REVOKED/EXPIRED。
     * @param tenantOwnerStatus Tenant 的 Owner 状态，不等同于凭证有效性。
     * @param tenantSecurityStatus Tenant 的安全限制状态。
     * @param expiresTime UTC 到期时刻，等于该时刻即过期。
     * @param version 当前行级并发版本。
     * @param createTime UTC 微秒创建时间。
     * @param updateTime UTC 微秒更新时间。
     */
    public record ApiCredentialSummary(
        String credentialId,
        String tenantId,
        String clientId,
        String clientName,
        String keyPrefix,
        String storedStatus,
        String status,
        String tenantOwnerStatus,
        String tenantSecurityStatus,
        Instant expiresTime,
        int version,
        Instant createTime,
        Instant updateTime) {}
    /** 只读凭证详情；授权明细使用独立分页接口。
     * @param credentialId 凭证标识，正十进制字符串。
     * @param tenantId 所属 CLIENT Tenant 标识。
     * @param clientId Tenant 已投影的 Client 主档标识。
     * @param clientName Tenant 当前显示名称。
     * @param keyPrefix 非秘密查找前缀，不包含完整凭证。
     * @param storedStatus 持久化状态，不被派生有效期覆盖。
     * @param status 按查询时刻派生的 ACTIVE/REVOKED/EXPIRED。
     * @param tenantOwnerStatus Tenant 的 Owner 状态，不等同于凭证有效性。
     * @param tenantSecurityStatus Tenant 的安全限制状态。
     * @param expiresTime UTC 到期时刻，等于该时刻即过期。
     * @param version 当前行级并发版本。
     * @param createTime UTC 微秒创建时间。
     * @param updateTime UTC 微秒更新时间。
     * @param rotatedFromId 同一 Tenant 的轮换来源凭证标识，未轮换时为 null。
     */
    public record ApiCredentialDetailResponseBody(
        String credentialId,
        String tenantId,
        String clientId,
        String clientName,
        String keyPrefix,
        String storedStatus,
        String status,
        String tenantOwnerStatus,
        String tenantSecurityStatus,
        Instant expiresTime,
        int version,
        Instant createTime,
        Instant updateTime,
        String rotatedFromId) {}
    /** 完整授权记录，不过滤已失效的记录。
     * @param scopeId 凭证授权明细标识，与目录主键不同。
     * @param scopeCode 精确授权编码；允许旧值未登记目录。
     * @param displayName 目录显示名称，未登记时为 null。
     * @param resourcePattern 原授权记录资源模式，仅只读展示。
     * @param actionCode 原授权记录动作代码，不新增运行语义。
     * @param storedStatus 持久化授权状态。
     * @param status 按自身区间派生的 ACTIVE/REVOKED/EXPIRED/PENDING，与父凭证状态分离。
     * @param effectiveFrom UTC 生效时间，包含该时刻。
     * @param effectiveTo UTC 失效时间，不含该时刻；null 表示无截止。
     * @param version 授权记录并发版本。
     * @param createTime UTC 微秒创建时间。
     * @param updateTime UTC 微秒更新时间。
     */
    public record ApiCredentialScopeGrant(
        String scopeId,
        String scopeCode,
        String displayName,
        String resourcePattern,
        String actionCode,
        String storedStatus,
        String status,
        Instant effectiveFrom,
        Instant effectiveTo,
        int version,
        Instant createTime,
        Instant updateTime) {}
    /** 撤销指定凭证，不级联其他凭证或授权行。
     * @param expectedVersion 必须匹配当前凭证版本。
     * @param reason 撤销原因，去除首尾空白后 1..500 字符。
     */
    public record RevokeApiCredentialRequestBody(
        @Positive int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}
    /** 撤销的稳定命令结果。
     * @param credentialId 本次撤销的凭证标识。
     * @param status 固定 REVOKED。
     * @param version 本次校验或保存后的凭证版本。
     */
    public record ApiCredentialRevokeResponseBody(
        String credentialId,
        String status,
        int version) {}
}
