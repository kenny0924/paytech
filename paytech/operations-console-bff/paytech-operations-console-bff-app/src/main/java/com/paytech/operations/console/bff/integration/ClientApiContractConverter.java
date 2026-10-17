package com.paytech.operations.console.bff.integration;

/** 非秘密管理合同的逐字段投影。 */
public final class ClientApiContractConverter {
    /** 只承载类型或静态转换，禁止创建无业务意义的实例。 */
    private ClientApiContractConverter() {}

    /** 仅复制已批准的字段。 */
    public static com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialSummary toBff(com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialSummaryResponseBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        require(id(value.getCredentialId())
            && id(value.getTenantId())
            && id(value.getClientId())
            && (value.getClientName() != null && !value.getClientName().isBlank())
            && (value.getTenantOwnerStatus() != null && !value.getTenantOwnerStatus().isBlank())
            && (value.getTenantSecurityStatus() != null && !value.getTenantSecurityStatus().isBlank())
            && (value.getKeyPrefix() != null && value.getKeyPrefix().matches("[A-Za-z0-9][A-Za-z0-9-]{7,31}"))
            && (value.getStatus() != null && java.util.Set.of("ACTIVE", "REVOKED", "EXPIRED").contains(value.getStatus()))
            && (value.getStoredStatus() != null && java.util.Set.of("ACTIVE", "REVOKED", "EXPIRED").contains(value.getStoredStatus()))
            && value.getVersion() > 0
            && value.getCreateTime() != null
            && value.getUpdateTime() != null
            && value.getExpiresTime() != null);
        return new com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialSummary(value.getCredentialId(), value.getTenantId(), value.getClientId(), value.getClientName(), value.getKeyPrefix(), value.getStoredStatus(), value.getStatus(), value.getTenantOwnerStatus(), value.getTenantSecurityStatus(), value.getExpiresTime(), value.getVersion(), value.getCreateTime(), value.getUpdateTime());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialDetailResponseBody toBff(com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialDetailResponseBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        require(id(value.getCredentialId())
            && id(value.getTenantId())
            && id(value.getClientId())
            && (value.getRotatedFromId() == null || id(value.getRotatedFromId()))
            && (value.getClientName() != null && !value.getClientName().isBlank())
            && (value.getTenantOwnerStatus() != null && !value.getTenantOwnerStatus().isBlank())
            && (value.getTenantSecurityStatus() != null && !value.getTenantSecurityStatus().isBlank())
            && (value.getKeyPrefix() != null && value.getKeyPrefix().matches("[A-Za-z0-9][A-Za-z0-9-]{7,31}"))
            && (value.getStatus() != null && java.util.Set.of("ACTIVE", "REVOKED", "EXPIRED").contains(value.getStatus()))
            && (value.getStoredStatus() != null && java.util.Set.of("ACTIVE", "REVOKED", "EXPIRED").contains(value.getStoredStatus()))
            && value.getVersion() > 0
            && value.getCreateTime() != null
            && value.getUpdateTime() != null
            && value.getExpiresTime() != null);
        return new com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialDetailResponseBody(value.getCredentialId(), value.getTenantId(), value.getClientId(), value.getClientName(), value.getKeyPrefix(), value.getStoredStatus(), value.getStatus(), value.getTenantOwnerStatus(), value.getTenantSecurityStatus(), value.getExpiresTime(), value.getVersion(), value.getCreateTime(), value.getUpdateTime(), value.getRotatedFromId());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialScopeGrant toBff(com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialScopeGrantResponseBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        require(id(value.getScopeId())
            && com.paytech.platform.support.api.apicredential.ApiCredentialConstraints.isValidScopeCode(value.getScopeCode())
            && (value.getDisplayName() == null || value.getDisplayName().length() <= 255)
            && (value.getStatus() != null && java.util.Set.of("ACTIVE", "REVOKED", "EXPIRED", "PENDING").contains(value.getStatus()))
            && (value.getStoredStatus() != null && java.util.Set.of("ACTIVE", "REVOKED", "EXPIRED").contains(value.getStoredStatus()))
            && value.getVersion() > 0
            && value.getCreateTime() != null
            && value.getUpdateTime() != null
            && value.getEffectiveFrom() != null);
        return new com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialScopeGrant(value.getScopeId(), value.getScopeCode(), value.getDisplayName(), value.getResourcePattern(), value.getActionCode(), value.getStoredStatus(), value.getStatus(), value.getEffectiveFrom(), value.getEffectiveTo(), value.getVersion(), value.getCreateTime(), value.getUpdateTime());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.platform.support.api.apicredentialmanagement.request.RevokeApiCredentialRequestBody toPlatform(com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.RevokeApiCredentialRequestBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        return new com.paytech.platform.support.api.apicredentialmanagement.request.RevokeApiCredentialRequestBody(value.expectedVersion(), value.reason());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialRevokeResponseBody toBff(com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialRevokeResponseBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        require(id(value.getCredentialId())
            && (value.getStatus() != null && java.util.Set.of("REVOKED").contains(value.getStatus()))
            && value.getVersion() > 0);
        return new com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.ApiCredentialRevokeResponseBody(value.getCredentialId(), value.getStatus(), value.getVersion());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeSummary toBff(com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeSummaryResponseBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        require(id(value.getApiScopeId())
            && com.paytech.platform.support.api.apicredential.ApiCredentialConstraints.isValidScopeCode(value.getScopeCode())
            && (value.getDisplayName() != null && !value.getDisplayName().isBlank())
            && value.getVersion() > 0
            && (value.getDescription() == null || value.getDescription().length() <= 500));
        return new com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeSummary(value.getApiScopeId(), value.getScopeCode(), value.getDisplayName(), value.getDescription(), value.getVersion());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeDetailResponseBody toBff(com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeDetailResponseBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        require(id(value.getApiScopeId())
            && com.paytech.platform.support.api.apicredential.ApiCredentialConstraints.isValidScopeCode(value.getScopeCode())
            && (value.getDisplayName() != null && !value.getDisplayName().isBlank())
            && value.getVersion() > 0
            && value.getCreateTime() != null
            && value.getUpdateTime() != null
            && (value.getDescription() == null || value.getDescription().length() <= 500));
        return new com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeDetailResponseBody(value.getApiScopeId(), value.getScopeCode(), value.getDisplayName(), value.getDescription(), value.getVersion(), value.getCreateTime(), value.getUpdateTime());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeOption toBff(com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeOptionResponseBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        require(com.paytech.platform.support.api.apicredential.ApiCredentialConstraints.isValidScopeCode(value.getScopeCode())
            && (value.getDisplayName() != null && !value.getDisplayName().isBlank()));
        return new com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeOption(value.getScopeCode(), value.getDisplayName());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.platform.support.api.apiscopemanagement.request.CreateApiScopeRequestBody toPlatform(com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.CreateApiScopeRequestBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        return new com.paytech.platform.support.api.apiscopemanagement.request.CreateApiScopeRequestBody(value.scopeCode(), value.displayName(), value.description(), value.reason());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.platform.support.api.apiscopemanagement.request.UpdateApiScopeRequestBody toPlatform(com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.UpdateApiScopeRequestBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        return new com.paytech.platform.support.api.apiscopemanagement.request.UpdateApiScopeRequestBody(value.displayName(), value.description(), value.expectedVersion(), value.reason());
    }

    /** 仅复制已批准的字段。 */
    public static com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeMutationResponseBody toBff(com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeMutationResponseBody value) {
        if (value == null) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
        require(id(value.getApiScopeId())
            && com.paytech.platform.support.api.apicredential.ApiCredentialConstraints.isValidScopeCode(value.getScopeCode())
            && value.getVersion() > 0);
        return new com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.ApiScopeMutationResponseBody(value.getApiScopeId(), value.getScopeCode(), value.getVersion());
    }
    /** 检查 Owner 响应身份；错误返回 502，不把别的对象显示给用户。 */
    public static void sameIdentity(String requested, String returned) { require(java.util.Objects.equals(requested, returned)); }

    /** 分页不改上游总数，检查窗口及当前页内身份唯一。 */
    public static <S, T> com.paytech.foundation.web.paging.PagingResponseBody<T> page(
        com.paytech.foundation.web.paging.PagingResponseBody<S> value, Integer page, Integer size,
        java.util.function.Function<S, T> converter, java.util.function.Function<T, String> identity) {
        int p = page == null ? 1 : page, s = size == null ? 10 : size;
        require(value != null && value.items() != null && value.page() == p && value.size() == s && p > 0 && s > 0 && s <= 100 && value.total() >= 0);
        long offset = ((long)p - 1) * s;
        require(value.items().size() == Math.min((long)s, Math.max(0L, value.total() - offset)));
        var items = value.items().stream().map(converter).toList();
        var identities = new java.util.HashSet<String>(); require(items.stream().allMatch(item -> identities.add(identity.apply(item))));
        return new com.paytech.foundation.web.paging.PagingResponseBody<>(items, p, s, value.total());
    }
    /** 检查上游十进制身份是否在有符号 Long 范围内。 */
    private static boolean id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) { return false; }
        try { return Long.parseLong(value) > 0; } catch (NumberFormatException invalid) { return false; }
    }
    /** 拒绝不符合已批准响应合同的 Owner 数据，保留 502 错误语义。 */
    private static void require(boolean valid) {
        if (!valid) { throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE); }
    }
}
