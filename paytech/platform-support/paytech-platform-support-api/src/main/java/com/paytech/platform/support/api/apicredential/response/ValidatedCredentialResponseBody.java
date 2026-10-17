package com.paytech.platform.support.api.apicredential.response;

import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;

/** PLT-I03 校验成功后提供给 Gateway 的可信 Client API 身份。 */
public final class ValidatedCredentialResponseBody {

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** 已验证的租户 ID。<li>1–19 位正十进制字符串。</li> */
    private final String tenantId;

    /** 租户类型。<li>固定为 CLIENT。</li> */
    private final String tenantType;

    /** CCT Client ID。<li>仅来自已验证租户的 Owner 引用。</li> */
    private final String ownerRefId;

    /** 已验证的 API 凭证 ID。<li>1–19 位正十进制字符串。</li> */
    private final String credentialId;

    /** 当前有效授权代码。<li>可为空，最多 64 项，按代码排序并去重，不可修改。</li> */
    private final List<String> effectiveScopeCodes;

    /** 按属性绑定可信身份，拒绝不完整身份和超限授权，保持 Gateway 失败关闭合同。 */
    @JsonCreator
    public ValidatedCredentialResponseBody(@JsonProperty("tenantId") String tenantId,
        @JsonProperty("tenantType") String tenantType, @JsonProperty("ownerRefId") String ownerRefId,
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("effectiveScopeCodes") List<String> effectiveScopeCodes) {
        if (!isValidId(tenantId) || !"CLIENT".equals(tenantType) || !isValidId(ownerRefId)
            || !isValidId(credentialId) || effectiveScopeCodes == null) {
            throw new IllegalArgumentException("PLT-I03 响应身份无效");
        }
        this.tenantId = tenantId;
        this.tenantType = tenantType;
        this.ownerRefId = ownerRefId;
        this.credentialId = credentialId;
        this.effectiveScopeCodes = validatedScopes(effectiveScopeCodes);
    }

    /** 返回已验证的租户标识。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 返回固定的 Client 租户类型。 */
    public String getTenantType() {
        return tenantType;
    }

    /** 返回租户对应的 Client 标识。 */
    public String getOwnerRefId() {
        return ownerRefId;
    }

    /** 返回已验证的凭证标识。 */
    public String getCredentialId() {
        return credentialId;
    }

    /** 返回不可变的有效授权代码。 */
    public List<String> getEffectiveScopeCodes() {
        return effectiveScopeCodes;
    }

    /** 校验数量及代码后规范化集合，不能靠去重掩盖超限响应。 */
    private static List<String> validatedScopes(List<String> scopes) {
        if (scopes.size() > ApiCredentialConstraints.MAX_EFFECTIVE_SCOPE_CODES) {
            throw new IllegalArgumentException("PLT-I03 响应 Scope 超过上限");
        }
        TreeSet<String> unique = new TreeSet<>();
        for (String scope : scopes) {
            if (!ApiCredentialConstraints.isValidScopeCode(scope)) {
                throw new IllegalArgumentException("PLT-I03 响应 Scope 无效");
            }
            unique.add(scope);
        }
        return List.copyOf(unique);
    }

    /** 校验 Wire 身份的正十进制字符串格式。 */
    private static boolean isValidId(String value) {
        return value != null && DECIMAL_ID.matcher(value).matches();
    }
}
