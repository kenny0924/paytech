package com.paytech.platform.support.accesscontrol.domain.apimanagement;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;

/** Client API 管理的字段边界；目录代码复用认证约束。 */
public final class ClientApiManagementInputs {
    /** 只承载类型或静态转换，禁止创建无业务意义的实例。 */
    private ClientApiManagementInputs() {}

    /** 复用 PLT-I03 的编码约束，禁止大小写改写或通配编码。 */
    public static String scopeCode(String value) {
        if (!ApiCredentialConstraints.isValidScopeCode(value)) { throw new InvalidParameterException("Scope 编码格式不合法"); }
        return value;
    }

    /** 校验必填的正十进制 Long 身份，不接受无值或溢出。 */
    public static long id(String value) {
        Long result = ConfigurationInputs.id(value);
        if (result == null) { throw new InvalidParameterException("ID 必填"); }
        return result;
    }

    /** 只接受可展示的密钥前缀格式，拒绝完整点分凭证。 */
    public static String prefix(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9-]{7,31}"))
            { throw new InvalidParameterException("密钥前缀必须为 8..32 位字母、数字或连字符"); }
        return value;
    }

    /** 将空说明规范为 null，并限制非空说明长度。 */
    public static String description(String value) {
        if (value == null || value.isBlank()) { return null; }
        return ConfigurationInputs.text(value, 500, "description");
    }

    /** 复用统一分页规则，并把本管理功能的上限收紧到 100。 */
    public static PagingRequestBody paging(Integer page, Integer size) {
        var value = PagingRequestBody.of(page, size);
        if (value.size() > 100) { throw new InvalidParameterException("每页最多 100 条"); }
        return value;
    }

    /** 把包含查询中的通配符转成文字，避免用户输入改变筛选语义。 */
    public static String contains(String value, int max, String name) {
        if (value == null) { return null; }
        String text = ConfigurationInputs.text(value, max, name);
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
