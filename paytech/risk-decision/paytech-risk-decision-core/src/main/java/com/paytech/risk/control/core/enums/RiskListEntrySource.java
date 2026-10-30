package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 名单成员来源。 */
public enum RiskListEntrySource implements CodeEnum {
    /** 规则动作写入，必须关联 Action Effect。 */
    ACTION("ACTION"),

    /** 运营人工维护，必须记录操作人与原因。 */
    MANUAL("MANUAL"),

    /** 限额档位投影，随档位变更同事务写入，原因记变更记录 ID。 */
    TIER("TIER");

    private final String code;

    RiskListEntrySource(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
