package com.paytech.platform.support.api.apiscopemanagement.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Scope 定义详情，引用查询由 BFF 按权限组合。 */
public class ApiScopeDetailResponseBody {

    /** Scope 目录主键，正十进制字符串。 */
    private final String apiScopeId;

    /** 不可修改的小写 ASCII 能力编码。 */
    private final String scopeCode;

    /** 可编辑展示名称，不参与认证。 */
    private final String displayName;

    /** 可空业务说明。 */
    private final String description;

    /** 目录行并发版本。 */
    private final int version;

    /** UTC 微秒创建时间。 */
    private final Instant createTime;

    /** UTC 微秒更新时间。 */
    private final Instant updateTime;

    /** 按 JSON 属性绑定，保留既有字段及可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiScopeDetailResponseBody(
        @JsonProperty("apiScopeId") String apiScopeId,
        @JsonProperty("scopeCode") String scopeCode,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("version") int version,
        @JsonProperty("createTime") Instant createTime,
        @JsonProperty("updateTime") Instant updateTime) {
        this.apiScopeId = apiScopeId;
        this.scopeCode = scopeCode;
        this.displayName = displayName;
        this.description = description;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /** Scope 目录主键，正十进制字符串。 */
    public String getApiScopeId() {
        return apiScopeId;
    }

    /** 不可修改的小写 ASCII 能力编码。 */
    public String getScopeCode() {
        return scopeCode;
    }

    /** 可编辑展示名称，不参与认证。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 可空业务说明。 */
    public String getDescription() {
        return description;
    }

    /** 目录行并发版本。 */
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
