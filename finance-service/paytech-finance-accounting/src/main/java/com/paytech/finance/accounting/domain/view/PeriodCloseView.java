package com.paytech.finance.accounting.domain.view;

import java.time.Instant;

import com.paytech.finance.accounting.core.enums.CloseStepType;
import com.paytech.finance.accounting.core.enums.PeriodCloseStatus;
import com.paytech.finance.accounting.core.enums.PeriodCloseType;

import lombok.Getter;
import lombok.Setter;

/** 关账流程摘要：流程根字段与首个失败步骤类型的组合读取结果。 */
@Getter
@Setter
public class PeriodCloseView {
    /** 流程主键。 */
    private Long periodCloseId;

    /** 会计期间主键。 */
    private Long periodId;

    /** 期间内流程序号。 */
    private Integer closeNo;

    /** 流程类型。 */
    private PeriodCloseType closeType;

    /** 流程状态。 */
    private PeriodCloseStatus status;

    /** 编制人。 */
    private Long makerId;

    /**
     * 复核人
     * <li>未复核时为空。</li>
     */
    private Long checkerId;

    /**
     * 关账摘要
     * <li>PENDING_CHECK 之前为空。</li>
     */
    private String closeHash;

    /**
     * 首个失败步骤
     * <li>未失败时为空。</li>
     */
    private CloseStepType failedStepType;

    /** 创建时间。 */
    private Instant createTime;
}
