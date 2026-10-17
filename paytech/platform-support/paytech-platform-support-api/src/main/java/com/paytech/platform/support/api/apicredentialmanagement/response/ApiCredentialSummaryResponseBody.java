package com.paytech.platform.support.api.apicredentialmanagement.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 后台凭证列表条目；凭证状态与 Tenant 状态分开展示，不代替真实认证。 */
public class ApiCredentialSummaryResponseBody {

    /** 凭证标识，正十进制字符串。 */
    private final String credentialId;

    /** 所属 CLIENT Tenant 标识，正十进制字符串。 */
    private final String tenantId;

    /** Tenant 已投影的 Client 主档标识。 */
    private final String clientId;

    /** Tenant 当前显示名称。 */
    private final String clientName;

    /** 非秘密查找前缀，不包含完整凭证。 */
    private final String keyPrefix;

    /** 持久化状态，不被派生有效期覆盖。 */
    private final String storedStatus;

    /** 按查询时刻派生的 ACTIVE、REVOKED 或 EXPIRED，不代表真实认证结果。 */
    private final String status;

    /** Tenant 的 Owner 状态，不等同于凭证有效性。 */
    private final String tenantOwnerStatus;

    /** Tenant 的安全限制状态。 */
    private final String tenantSecurityStatus;

    /** UTC 到期时刻；等于该时刻即过期。 */
    private final Instant expiresTime;

    /** 当前行级并发版本。 */
    private final int version;

    /** UTC 微秒创建时间。 */
    private final Instant createTime;

    /** UTC 微秒更新时间。 */
    private final Instant updateTime;

    /** 按既有 JSON 属性构造凭证摘要，保留 HTTP 字段与时间合同。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiCredentialSummaryResponseBody(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("clientId") String clientId,
        @JsonProperty("clientName") String clientName,
        @JsonProperty("keyPrefix") String keyPrefix,
        @JsonProperty("storedStatus") String storedStatus,
        @JsonProperty("status") String status,
        @JsonProperty("tenantOwnerStatus") String tenantOwnerStatus,
        @JsonProperty("tenantSecurityStatus") String tenantSecurityStatus,
        @JsonProperty("expiresTime") Instant expiresTime,
        @JsonProperty("version") int version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime) {
        this.credentialId = credentialId;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.keyPrefix = keyPrefix;
        this.storedStatus = storedStatus;
        this.status = status;
        this.tenantOwnerStatus = tenantOwnerStatus;
        this.tenantSecurityStatus = tenantSecurityStatus;
        this.expiresTime = expiresTime;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /** 凭证标识，正十进制字符串。 */
    public String getCredentialId() {
        return credentialId;
    }

    /** 所属 CLIENT Tenant 标识，正十进制字符串。 */
    public String getTenantId() {
        return tenantId;
    }

    /** Tenant 已投影的 Client 主档标识。 */
    public String getClientId() {
        return clientId;
    }

    /** Tenant 当前显示名称。 */
    public String getClientName() {
        return clientName;
    }

    /** 非秘密查找前缀，不包含完整凭证。 */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /** 持久化状态，不被派生有效期覆盖。 */
    public String getStoredStatus() {
        return storedStatus;
    }

    /** 按查询时刻派生的 ACTIVE、REVOKED 或 EXPIRED，不代表真实认证结果。 */
    public String getStatus() {
        return status;
    }

    /** Tenant 的 Owner 状态，不等同于凭证有效性。 */
    public String getTenantOwnerStatus() {
        return tenantOwnerStatus;
    }

    /** Tenant 的安全限制状态。 */
    public String getTenantSecurityStatus() {
        return tenantSecurityStatus;
    }

    /** UTC 到期时刻；等于该时刻即过期。 */
    public Instant getExpiresTime() {
        return expiresTime;
    }

    /** 当前行级并发版本。 */
    public int getVersion() {
        return version;
    }

    /** UTC 微秒创建时间。 */
    public Instant getCreateTime() {
        return createTime;
    }

    /** UTC 微秒更新时间。 */
    public Instant getUpdateTime() {
        return updateTime;
    }
}
