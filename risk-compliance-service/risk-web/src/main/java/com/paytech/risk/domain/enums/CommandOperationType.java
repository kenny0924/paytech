package com.paytech.risk.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

public enum CommandOperationType implements CodeEnum {

    CREATE_RULE_SET_VERSION,

    FREEZE_RULE_SET_VERSION,

    CREATE_TEST_DECISION_RELEASE,

    VALIDATE_TEST_DECISION_RELEASE,

    DELETE_RULE_SET,

    SAVE_DECISION_FLOW,

    DELETE_DECISION_FLOW;

    @Override
    public String getCode() {
        return switch (this) {
            case CREATE_RULE_SET_VERSION -> "CREATE_RULE_SET_VERSION";
            case FREEZE_RULE_SET_VERSION -> "FREEZE_RULE_SET_VERSION";
            case CREATE_TEST_DECISION_RELEASE -> "CREATE_TEST_DECISION_RELEASE";
            case VALIDATE_TEST_DECISION_RELEASE -> "VALIDATE_TEST_DECISION_RELEASE";
            case DELETE_RULE_SET -> "DELETE_RULE_SET";
            case SAVE_DECISION_FLOW -> "SAVE_DECISION_FLOW";
            case DELETE_DECISION_FLOW -> "DELETE_DECISION_FLOW";
        };
    }
}
