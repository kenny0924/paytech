package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 尽调案件状态（t_due_diligence_case.status，design 6.1 SM-D）
 * <li>进行中（active_slot=1）：SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、PENDING_DEPENDENCY。</li>
 * <li>RETURNED 释放进行中占位（退回关案，重提另建新案件串联），但在补件期限内仍可被撤回或过期。</li>
 * <li>本期不写 DRAFT、WAITING_EVIDENCE、PENDING_DEPENDENCY：子案件随主案件一起决定，筛查异步执行不改变案件状态。</li>
 */
public enum DueDiligenceCaseStatus implements CodeEnum {
    DRAFT("DRAFT"),

    SUBMITTED("SUBMITTED"),

    IN_REVIEW("IN_REVIEW"),

    WAITING_EVIDENCE("WAITING_EVIDENCE"),

    PENDING_DEPENDENCY("PENDING_DEPENDENCY"),

    RETURNED("RETURNED"),

    APPROVED("APPROVED"),

    REJECTED("REJECTED"),

    WITHDRAWN("WITHDRAWN"),

    EXPIRED("EXPIRED");

    private final String code;

    /** 固定业务代码，不依赖 Java 枚举名称。 */
    DueDiligenceCaseStatus(String code) {
        this.code = code;
    }

    /** 返回稳定业务代码。 */
    @Override
    public String getCode() {
        return code;
    }

    /** 是否进行中（占用 active_slot）。 */
    public boolean inProgress() {
        return this == SUBMITTED || this == IN_REVIEW || this == WAITING_EVIDENCE || this == PENDING_DEPENDENCY;
    }

    /** 是否终态：APPROVED、REJECTED、WITHDRAWN、EXPIRED。 */
    public boolean terminal() {
        return this == APPROVED || this == REJECTED || this == WITHDRAWN || this == EXPIRED;
    }

    /** 按代码查找，未知代码返回 null 由调用方 Fail-Closed。 */
    public static DueDiligenceCaseStatus ofCode(String code) {
        for (DueDiligenceCaseStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
