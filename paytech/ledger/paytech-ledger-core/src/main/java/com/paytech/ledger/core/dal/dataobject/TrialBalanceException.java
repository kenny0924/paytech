package com.paytech.ledger.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.core.enums.TrialExceptionType;
import com.paytech.ledger.core.enums.TrialReferenceType;

import lombok.Getter;
import lombok.Setter;

/** t_trial_balance_exception：记录账户、往来、在途、对账或科目汇总之间阻断关账的不一致及其解决引用，并核对平台服务费双账簿应付与应收。 */
@Getter
@Setter
public class TrialBalanceException {
    /**
     * 试算平衡异常的 Snowflake 主键
     */
    private Long id;

    /**
     * 发现该异常的本库试算平衡运行
     */
    private Long runId;

    /**
     * 试算平衡完整性异常的受控分类；COUNTERPARTY_MISMATCH 还覆盖同一 Economics、Component 和币种下 PLATFORM_SERVICE_PAYABLE 与 PLATFORM_SERVICE_RECEIVABLE 不相等；CHANNEL_SETTLEMENT_MISMATCH 覆盖 Channel 待清算、结算应收应付与已确认 Bank Statement 之间的阶段差异
     * <li>允许值：COUNTERPARTY_MISMATCH、CHANNEL_SETTLEMENT_MISMATCH、GENERAL_LEDGER_MISMATCH、UNRESOLVED_RECONCILIATION。</li>
     */
    private TrialExceptionType exceptionType;

    /**
     * 明确异常所引用的统一账户、会计科目、银行账户或对账异常记录类型
     * <li>允许值：ACCOUNT、CHART_ACCOUNT、CASH_ACCOUNT、RECON_EXCEPTION。</li>
     */
    private TrialReferenceType referenceType;

    /**
     * 按异常引用类型解释且用于定位被核对对象的稳定主键
     */
    private Long referenceId;

    /**
     * 核对结果之间的绝对差异金额
     */
    private BigDecimal differenceAmount;

    /**
     * 试算平衡异常当前处置状态
     * <li>允许值：OPEN、UNDER_REVIEW、RESOLVED、WAIVED。</li>
     */
    private TrialExceptionStatus status;

    /**
     * 终态异常采用的受控解决证据类型；PROJECTION_REBUILD 只从 Journal 重建投影且不得追加 Journal
     * <li>允许值：RECON_RESOLUTION、ACCOUNTING_INSTRUCTION、PROJECTION_REBUILD、CONTROLLED_WAIVER。</li>
     * <li>可为空。</li>
     */
    private String resolutionType;

    /**
     * 批准的对账处置、会计指令、投影重建命令结果或 Platform 控制豁免的稳定标识
     * <li>可为空。</li>
     */
    private Long resolutionRefId;

    /**
     * 异常、解决类型、证据标识和决定的 Canonical SHA-256 哈希
     * <li>可为空。</li>
     */
    private String resolutionContentHash;

    /**
     * Platform 身份系统中提交该异常解决方案的操作人标识
     * <li>可为空。</li>
     */
    private Long resolutionMakerId;

    /**
     * Platform 身份系统中独立批准该异常解决方案的操作人标识
     * <li>可为空。</li>
     */
    private Long resolutionCheckerId;

    /**
     * 双人控制完成并使异常进入终态的 UTC 微秒时间
     * <li>可为空。</li>
     */
    private Instant resolutionTime;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;

    /**
     * 该可变记录最后一次持久化更新的 UTC 微秒时间
     */
    private Instant updateTime;
}
