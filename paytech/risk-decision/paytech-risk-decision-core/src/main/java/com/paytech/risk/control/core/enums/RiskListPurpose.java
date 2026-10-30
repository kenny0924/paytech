package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 名单用途，仅用于展示与规则命名约定；命中后的处置由规则决定
 * <li>TIER 为系统维护的限额档位名单（REQ-04）：只由初始化 DML 创建，成员随档位变更同事务维护，运营不能增删成员。</li>
 */
public enum RiskListPurpose implements CodeEnum {
    /** 阻断类名单。 */
    BLOCK("BLOCK"),

    /** 放行类名单。 */
    ALLOW("ALLOW"),

    /** 限额档位名单，系统维护。 */
    TIER("TIER");

    private final String code;

    RiskListPurpose(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
