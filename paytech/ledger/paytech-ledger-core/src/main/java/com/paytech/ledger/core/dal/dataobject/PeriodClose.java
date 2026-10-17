package com.paytech.ledger.core.dal.dataobject;

import java.time.Instant;

import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodCloseType;

import lombok.Getter;
import lombok.Setter;

/** t_period_close：控制会计期间关账或重开流程、活动槽、试算水位和固定双人审批内容。 */
@Getter
@Setter
public class PeriodClose {
    /**
     * 期间关账或重开聚合根的 Snowflake 主键
     */
    private Long id;

    /**
     * 被关账或重开的本库会计期间
     */
    private Long periodId;

    /**
     * 同一会计期间内从一开始连续递增的流程序号
     */
    private Integer closeNo;

    /**
     * 区分期间关账与受控重开
     * <li>允许值：CLOSE、REOPEN。</li>
     */
    private PeriodCloseType closeType;

    /**
     * 重开流程唯一引用同一期间已完成的原关账流程；普通关账为空
     * <li>可为空。</li>
     */
    private Long reopensCloseId;

    /**
     * 重开会计期间采用的受控业务原因；普通关账为空
     * <li>取值来源：Finance 期间重开原因目录；非固定枚举。</li>
     * <li>可为空。</li>
     */
    private String reopenReasonCode;

    /**
     * 批准发起本次重开的 Platform 工单或操作申请稳定引用；普通关账为空
     * <li>可为空。</li>
     */
    private String reopenRequestRef;

    /**
     * 期间关账或重开流程当前状态
     * <li>允许值：CREATED、RUNNING、PENDING_CHECK、APPROVED、REJECTED、COMPLETED、FAILED。</li>
     */
    private PeriodCloseStatus status;

    /**
     * 流程非终态时固定为 1，终态后必须固定为 NULL
     * <li>可为空。</li>
     */
    private Integer activeSlot;

    /**
     * Platform 身份系统中的关账或重开提交人标识
     */
    private Long makerId;

    /**
     * Platform 身份系统中的独立关账或重开复核人标识
     * <li>可为空。</li>
     */
    private Long checkerId;

    /**
     * 流程进入 PENDING_CHECK 时冻结的流程头、全部账簿范围、步骤、试算、异常解决和结转指令的 Canonical SHA-256 哈希；CREATED/RUNNING/FAILED 时为空
     * <li>可为空。</li>
     */
    private String closeHash;

    /**
     * 关账或重开聚合根条件更新使用的乐观锁版本
     */
    private Long version;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
