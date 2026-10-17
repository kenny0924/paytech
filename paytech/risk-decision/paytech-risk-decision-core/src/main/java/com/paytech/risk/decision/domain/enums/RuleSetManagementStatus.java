package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 规则集整体管理生命周期；与配置版本的冻结和发布状态独立。 */
public enum RuleSetManagementStatus implements CodeEnum {
    CONFIGURABLE("CONFIGURABLE"),

    DELETED("DELETED");

    private final String code;

    /** 固定持久化代码，禁止通过枚举序号推导。 */
    RuleSetManagementStatus(String code) {
        this.code = code;
    }

    /** 返回稳定数据库代码。 */
    @Override
    public String getCode() {
        return code;
    }
}
