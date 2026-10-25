package com.paytech.operations.console.bff.domain.enums;

import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 资格登记前判定主体已终结的 client-master 状态（client-master-api 未发布状态枚举，此为消费方定义）
 * <li>OE 为 CLOSED；Local Partner 与 Client 为 TERMINATED。</li>
 */
public enum QualificationSubjectTerminalStatus implements CodeEnum {
    /** 经营主体已关闭 */
    CLOSED("CLOSED"),

    /** Local Partner 或 Client 已终止 */
    TERMINATED("TERMINATED");

    private final String code;

    QualificationSubjectTerminalStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /**
     * 判断主体当前状态是否为该角色的终结状态
     *
     * @param roleType 资格角色类型
     * @param status client-master 返回的状态 Code
     * @return OE 为 CLOSED、其他角色为 TERMINATED 时为 true
     */
    public static boolean isTerminal(QualificationRoleType roleType, String status) {
        QualificationSubjectTerminalStatus terminal = roleType == QualificationRoleType.OPERATING_ENTITY ? CLOSED : TERMINATED;
        return terminal.code.equals(status);
    }
}
