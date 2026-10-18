package com.paytech.platform.support.api.apicredentialmanagement.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 凭证授权明细；保留未来和失效记录，状态独立于父凭证。 */
public class ApiCredentialScopeGrantResponseBody {

    /** 凭证授权明细标识，与目录主键不同。 */
    private final String scopeId;

    /** 精确授权编码；允许旧值未登记目录。 */
    private final String scopeCode;

    /** 目录显示名称，未登记时为 null。 */
    private final String displayName;

    /** 原授权记录资源模式，仅只读展示。 */
    private final String resourcePattern;

    /** 原授权记录动作代码，不新增运行语义。 */
    private final String actionCode;

    /** 持久化授权状态。 */
    private final String storedStatus;

    /** 按自身区间派生的 ACTIVE/REVOKED/EXPIRED/PENDING，与父凭证状态分离。 */
    private final String status;

    /** UTC 生效时间，包含该时刻。 */
    private final Instant effectiveFrom;

    /** UTC 失效时间，不含该时刻；null 表示无截止。 */
    private final Instant effectiveTo;

    /** 授权记录并发版本。 */
    private final int version;

    /** UTC 微秒创建时间。 */
    private final Instant createTime;

    /** UTC 微秒更新时间。 */
    private final Instant updateTime;

    /** 按既有 JSON 属性绑定，保持字段和可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiCredentialScopeGrantResponseBody(
        @JsonProperty("scopeId") String scopeId,
        @JsonProperty("scopeCode") String scopeCode,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("resourcePattern") String resourcePattern,
        @JsonProperty("actionCode") String actionCode,
        @JsonProperty("storedStatus") String storedStatus,
        @JsonProperty("status") String status,
        @JsonProperty("effectiveFrom") Instant effectiveFrom,
        @JsonProperty("effectiveTo") Instant effectiveTo,
        @JsonProperty("version") int version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime) {
        this.scopeId = scopeId;
        this.scopeCode = scopeCode;
        this.displayName = displayName;
        this.resourcePattern = resourcePattern;
        this.actionCode = actionCode;
        this.storedStatus = storedStatus;
        this.status = status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /** 凭证授权明细标识，与目录主键不同。 */
    public String getScopeId() {
        return scopeId;
    }

    /** 精确授权编码；允许旧值未登记目录。 */
    public String getScopeCode() {
        return scopeCode;
    }

    /** 目录显示名称，未登记时为 null。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 原授权记录资源模式，仅只读展示。 */
    public String getResourcePattern() {
        return resourcePattern;
    }

    /** 原授权记录动作代码，不新增运行语义。 */
    public String getActionCode() {
        return actionCode;
    }

    /** 持久化授权状态。 */
    public String getStoredStatus() {
        return storedStatus;
    }

    /** 按自身区间派生的 ACTIVE/REVOKED/EXPIRED/PENDING，与父凭证状态分离。 */
    public String getStatus() {
        return status;
    }

    /** UTC 生效时间，包含该时刻。 */
    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    /** UTC 失效时间，不含该时刻；null 表示无截止。 */
    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    /** 授权记录并发版本。 */
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
