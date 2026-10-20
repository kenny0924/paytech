package com.paytech.ledger.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 一条科目变更记录。 */
@Getter
@Setter
public class ChartAccountChangeResponseBody {
    /** 变更记录主键。 */
    private String changeId;

    /** 被变更的科目主键。 */
    private String chartAccountId;

    /** 变更后科目编号。 */
    private String accountNo;

    /** 变更类型：CREATE、UPDATE、DISABLE 或 ENABLE。 */
    private String changeType;

    /**
     * 变更前科目编号
     * <li>CREATE 为空。</li>
     */
    private String beforeAccountNo;

    /** 变更后科目编号。 */
    private String afterAccountNo;

    /**
     * 变更前上级科目编号
     * <li>CREATE 或原为一级科目时为空；显示上级科目的当前编号。</li>
     */
    private String beforeParentAccountNo;

    /**
     * 变更后上级科目编号
     * <li>一级科目为空；显示上级科目的当前编号。</li>
     */
    private String afterParentAccountNo;

    /**
     * 变更前科目名称
     * <li>CREATE 为空。</li>
     */
    private String beforeAccountName;

    /** 变更后科目名称。 */
    private String afterAccountName;

    /**
     * 变更前科目状态
     * <li>ACTIVE 或 INACTIVE；CREATE 为空。</li>
     */
    private String beforeAccountStatus;

    /** 变更后科目状态：ACTIVE 或 INACTIVE。 */
    private String afterAccountStatus;

    /** 操作人 Account ID（X-Gateway-Actor-Subject）。 */
    private String operatorId;

    /** 变更时间（UTC）。 */
    private Instant changeTime;
}
