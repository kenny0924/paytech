package com.paytech.client.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Platform 公开的工作区受限原因码及客户可见文案；内部暂停理由不进入页面。 */
public enum WorkspaceRestrictionReason implements CodeMessageEnum {
    TENANT_TERMINATED("TENANT_TERMINATED", "工作区已终止"),

    TENANT_SUSPENDED("TENANT_SUSPENDED", "工作区已暂停"),

    TENANT_SECURITY_SUSPENDED("TENANT_SECURITY_SUSPENDED", "工作区暂时不可访问"),

    MEMBER_INACTIVE("MEMBER_INACTIVE", "您的成员资格已失效");

    private final String code;
    private final String message;

    WorkspaceRestrictionReason(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    /**
     * 按 Code 解析受限原因。
     *
     * @param code Platform 原因码
     * @return 对应枚举；未知或为空返回 null，由调用方按依赖响应无效处理
     */
    public static WorkspaceRestrictionReason fromCode(String code) {
        for (WorkspaceRestrictionReason reason : values()) {
            if (reason.code.equals(code)) {
                return reason;
            }
        }
        return null;
    }
}
