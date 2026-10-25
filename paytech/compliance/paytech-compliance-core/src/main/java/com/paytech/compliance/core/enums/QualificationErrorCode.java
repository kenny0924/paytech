package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 角色资格查询、手工登记与撤销的业务失败
 * <li>与 compliance-api 发布的同名 Code 一致；业务模块不依赖 API 模块，故在 Owner 内部声明。</li>
 * <li>HTTP 状态由 ComplianceApiErrorConfiguration 映射：NOT_FOUND 404、CHANGED 与 NOT_ACTIVE 409、INVALID 422。</li>
 */
public enum QualificationErrorCode implements CodeMessageEnum {
    QUALIFICATION_NOT_FOUND("QUALIFICATION_NOT_FOUND", "角色资格不存在"),

    QUALIFICATION_CHANGED("QUALIFICATION_CHANGED", "角色资格已被其他操作改变，请刷新后确认"),

    QUALIFICATION_NOT_ACTIVE("QUALIFICATION_NOT_ACTIVE", "角色资格当前不是已批准状态"),

    QUALIFICATION_INVALID("QUALIFICATION_INVALID", "角色、资格类型或有效期不符合登记规则");

    private final String code;

    private final String message;

    /** 固定 Wire 错误代码与可外显的安全业务说明。 */
    QualificationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 返回稳定 Wire 错误代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 返回默认可外显的业务说明。 */
    @Override
    public String getMessage() {
        return message;
    }
}
