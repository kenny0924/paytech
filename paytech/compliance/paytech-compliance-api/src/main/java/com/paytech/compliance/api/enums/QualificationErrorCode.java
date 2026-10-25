package com.paytech.compliance.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * 角色资格判定、运营登记与撤销可由调用方分别处理的失败
 * <li>QUALIFICATION_NOT_FOUND 404：资格不存在；判定接口对非 commercial-service 调用方同样返回该错误。</li>
 * <li>QUALIFICATION_CHANGED 409：资格已被其他操作改变，页面需刷新后重试。</li>
 * <li>QUALIFICATION_NOT_ACTIVE 409：撤销时资格当前不是 APPROVED。</li>
 * <li>QUALIFICATION_INVALID 422：角色与资格类型组合、角色标识格式或有效期不合法。</li>
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
