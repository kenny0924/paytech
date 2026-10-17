package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 权限目录已有的风险等级；未知持久化代码由共享 TypeHandler 拒绝。 */
public enum OperationsPermissionRiskLevel implements CodeEnum {
    LOW("LOW"),

    MEDIUM("MEDIUM"),

    HIGH("HIGH"),

    CRITICAL("CRITICAL");

    private final String code;

    OperationsPermissionRiskLevel(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
