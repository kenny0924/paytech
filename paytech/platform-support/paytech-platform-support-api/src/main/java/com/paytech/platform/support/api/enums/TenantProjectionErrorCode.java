package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** 私网来源投影的稳定错误，区分永久冲突和缺少前序事实。 */
public enum TenantProjectionErrorCode implements CodeMessageEnum {
    TENANT_APPLICANT_INVALID("TENANT_APPLICANT_INVALID", "申请人不满足工作区开通条件"),

    TENANT_PROJECTION_CONFLICT("TENANT_PROJECTION_CONFLICT", "来源身份或投影内容冲突"),

    TENANT_PROJECTION_STALE("TENANT_PROJECTION_STALE", "开通命令早于当前来源版本"),

    TENANT_PROJECTION_NOT_READY("TENANT_PROJECTION_NOT_READY", "等待开通或前序来源投影");

    private final String code;
    private final String message;

    /** 绑定稳定代码及不包含身份原值的消息。 */
    TenantProjectionErrorCode(String code, String message) { this.code = code; this.message = message; }
    /** 返回协议错误码。 */
    @Override public String getCode() { return code; }
    /** 返回可安全记录的提示。 */
    @Override public String getMessage() { return message; }
}
