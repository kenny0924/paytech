package com.paytech.client.master.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** 全球合规入驻新增接口中调用方需要分别处理的错误；消息为可外显的安全说明，不含客户资料。 */
public enum OnboardingErrorCode implements CodeMessageEnum {
    /** 入驻码不存在、已作废、已过期、已用尽或签发方不再具备 OE_PROVIDER 角色；不区分原因，不泄露 Partner（422）。 */
    ONBOARDING_CODE_INVALID("ONBOARDING_CODE_INVALID", "入驻码无效或已失效"),

    /** 辖区不存在、未启用、准入策略为 CLOSED，或该辖区没有可用的采集 Schema（422）。 */
    JURISDICTION_NOT_ACCEPTED("JURISDICTION_NOT_ACCEPTED", "当前辖区暂不受理入驻"),

    /** 申请只能在 DRAFT 或 RETURNED 时撤回（409）。 */
    APPLICATION_NOT_WITHDRAWABLE("APPLICATION_NOT_WITHDRAWABLE", "当前申请状态不能撤回"),

    /** 入驻码不存在或不属于该 Local Partner（404）。 */
    ONBOARDING_CODE_NOT_FOUND("ONBOARDING_CODE_NOT_FOUND", "入驻码不存在"),

    /** 只有 ACTIVE 且具备 ACTIVE OE_PROVIDER 角色的 Local Partner 可以签发入驻码（422）。 */
    PARTNER_NOT_OE_PROVIDER("PARTNER_NOT_OE_PROVIDER", "该 Local Partner 不能签发入驻码");

    private final String code;
    private final String message;

    /** 固定 Code 与默认消息。 */
    OnboardingErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 可外显的默认消息。 */
    @Override
    public String getMessage() {
        return message;
    }
}
