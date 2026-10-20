package com.paytech.ledger.domain.view;

import java.time.Instant;

import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;

import lombok.Getter;
import lombok.Setter;

/** 科目变更记录与变更前后上级科目编号的组合读取结果。 */
@Getter
@Setter
public class ChartAccountChangeView {
    /** 变更记录主键。 */
    private Long changeId;

    /** 被变更的科目主键。 */
    private Long chartAccountId;

    /** 变更类型。 */
    private ChartAccountChangeType changeType;

    /** 变更前科目编号；CREATE 为空。 */
    private String beforeAccountNo;

    /** 变更后科目编号。 */
    private String afterAccountNo;

    /** 变更前上级科目的当前编号；CREATE 或原为一级科目时为空。 */
    private String beforeParentAccountNo;

    /** 变更后上级科目的当前编号；一级科目为空。 */
    private String afterParentAccountNo;

    /** 变更前科目名称；CREATE 为空。 */
    private String beforeAccountName;

    /** 变更后科目名称。 */
    private String afterAccountName;

    /** 变更前科目状态；CREATE 为空。 */
    private ChartAccountStatus beforeAccountStatus;

    /** 变更后科目状态。 */
    private ChartAccountStatus afterAccountStatus;

    /** 操作人 Account ID。 */
    private String operatorId;

    /** 变更时间（UTC）。 */
    private Instant changeTime;
}
