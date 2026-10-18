package com.paytech.platform.support.auditsecurity.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 审计操作主体类型；对应 t_audit_event.actor_type，不参与 V1 事件哈希。 */
public enum AuditActorType implements CodeEnum {
    /** 人工账号。 */
    USER("USER"),

    /** 内部服务调用方，主体标识为服务名。 */
    WORKLOAD("WORKLOAD");

    private final String code;

    AuditActorType(String code) {
        this.code = code;
    }

    /** 稳定 Code。 */
    @Override
    public String getCode() {
        return code;
    }
}
