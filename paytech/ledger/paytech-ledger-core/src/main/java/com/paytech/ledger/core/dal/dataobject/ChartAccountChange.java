package com.paytech.ledger.core.dal.dataobject;

import java.time.Instant;

import com.paytech.ledger.core.enums.ChartAccountChangeType;
import com.paytech.ledger.core.enums.ChartAccountStatus;

import lombok.Getter;
import lombok.Setter;

/** t_chart_account_change：科目新增、修改、停用、启用的不可变变更记录；科目维护不审批、直接生效，以本表留痕。 */
@Getter
@Setter
public class ChartAccountChange {
    /**
     * 科目变更记录的服务侧雪花主键
     */
    private Long id;

    /**
     * 被变更科目所属的科目表
     */
    private Long chartId;

    /**
     * 被变更的科目
     */
    private Long chartAccountId;

    /**
     * 变更类型
     * <li>允许值：CREATE、UPDATE、DISABLE、ENABLE。</li>
     */
    private ChartAccountChangeType changeType;

    /**
     * 变更前科目编号
     * <li>CREATE 为空。</li>
     */
    private String beforeAccountNo;

    /**
     * 变更后科目编号
     */
    private String afterAccountNo;

    /**
     * 变更前上级科目
     * <li>CREATE 或原为一级科目时为空。</li>
     */
    private Long beforeParentAccountId;

    /**
     * 变更后上级科目
     * <li>一级科目为空。</li>
     */
    private Long afterParentAccountId;

    /**
     * 变更前科目名称
     * <li>CREATE 为空。</li>
     */
    private String beforeAccountName;

    /**
     * 变更后科目名称
     */
    private String afterAccountName;

    /**
     * 变更前科目状态
     * <li>CREATE 为空；允许值：ACTIVE、INACTIVE。</li>
     */
    private ChartAccountStatus beforeAccountStatus;

    /**
     * 变更后科目状态
     * <li>允许值：ACTIVE、INACTIVE。</li>
     */
    private ChartAccountStatus afterAccountStatus;

    /**
     * 执行变更的运营人员账号标识，取自 Gateway 传递的 X-Gateway-Actor-Subject
     */
    private String operatorId;

    /**
     * 变更在 Finance 数据库中生效的 UTC 微秒时间；记录只追加，不更新、不删除
     */
    private Instant changeTime;
}
