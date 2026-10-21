package com.paytech.reconciliation.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;

import lombok.Getter;
import lombok.Setter;

/**
 * t_recon_exception（对账异常）
 * <li>对账异常：对不上的项，挂在本库某个对象上，经人工处置结案；只显示账龄，不做分派。</li>
 */
@Getter
@Setter
public class ReconException {
    /**
     * 对账异常ID
     * <li>Snowflake 主键。</li>
     */
    private Long id;

    /**
     * 异常类型
     * <li>异常类型。允许值：CHANNEL_LINE_UNMATCHED、AMBIGUOUS_REFERENCE、AMOUNT_MISMATCH、DUPLICATE_SETTLEMENT、UNEXPLAINED_ADJUSTMENT、COST_DEVIATION、PLATFORM_ITEM_UNSETTLED、BANK_LINE_UNMATCHED、BANK_AMOUNT_MISMATCH、BANK_MOVEMENT_OVERDUE、BANK_STATEMENT_GAP、LATE_DEBIT。</li>
     */
    private ReconExceptionType exceptionType;

    /**
     * 对账层
     * <li>所属对账层。允许值：TRANSACTION、BANK。</li>
     */
    private ReconLayer reconLayer;

    /**
     * 是否阻塞
     * <li>1 表示阻塞所属渠道账单对平，0 表示不阻塞（PLATFORM_ITEM_UNSETTLED、BANK_MOVEMENT_OVERDUE、BANK_STATEMENT_GAP 及银行层异常为 0）。</li>
     */
    private Boolean blocking;

    /**
     * 挂靠对象类型
     * <li>异常挂靠的本库对象类型，决定可用处置与归类码。允许值：CHANNEL_STATEMENT、CHANNEL_STATEMENT_LINE、PLATFORM_TXN、BANK_STATEMENT、BANK_STATEMENT_LINE、EXPECTED_BANK_MOVEMENT。</li>
     */
    private ReconExceptionSourceType sourceType;

    /**
     * 挂靠对象ID
     * <li>挂靠对象的本库 ID。</li>
     */
    private Long sourceId;

    /**
     * 渠道账单ID
     * <li>交易层异常所属的渠道账单，用于对平门禁查询；无关时为空。</li>
     */
    private Long statementId;

    /**
     * 清算账户ID
     * <li>由来源父链确定的清算账户；银行层异常为空。</li>
     */
    private Long clearingAccountId;

    /**
     * 银行账户ID
     * <li>由来源父链确定的银行账户；交易层异常为空。</li>
     */
    private Long cashAccountId;

    /**
     * 主体类型
     * <li>由来源父链确定的主体类型，不得人工改挂。允许值：OPERATING_ENTITY、FINANCE_ENTITY。</li>
     */
    private ReconEntityType entityType;

    /**
     * 主体标识
     * <li>由来源父链确定的主体标识：经营主体为 entityNo，财务主体为 ledger 主键十进制字符串。</li>
     */
    private String entityId;

    /**
     * 币种
     * <li>币种。取值来源：ISO 4217 币种代码；非固定枚举。</li>
     */
    private String currency;

    /**
     * 差异金额
     * <li>系统算出的待处置差异（带符号）；归类处置金额必须与之相等。</li>
     */
    private BigDecimal differenceAmount;

    /**
     * 说明
     * <li>系统生成的说明，如命中的多条平台流水 ID、重复的账单行。</li>
     */
    private String detail;

    /**
     * 状态
     * <li>异常状态。允许值：OPEN、UNDER_REVIEW、RESOLVED、AUTO_CLOSED。</li>
     */
    private ReconExceptionStatus status;

    /**
     * 活动槽位
     * <li>OPEN、UNDER_REVIEW 时为 1，RESOLVED、AUTO_CLOSED 时为 NULL；保证同一对象同一类型只有一个未结异常。</li>
     */
    private Integer activeSlot;

    /**
     * 开立人ID
     * <li>人工开异常的 Platform 账户 ID；系统开的为空。</li>
     */
    private Long openedBy;

    /**
     * 开立时间
     * <li>开立的 UTC 时间，用于账龄。</li>
     */
    private Instant openedTime;

    /**
     * 结案时间
     * <li>结案的 UTC 时间。</li>
     */
    private Instant closedTime;

    /**
     * 乐观锁版本
     * <li>状态更新的乐观锁版本。</li>
     */
    private Long version;

    /**
     * 创建时间
     * <li>创建的 UTC 微秒时间。</li>
     */
    private Instant createTime;

    /**
     * 更新时间
     * <li>最后更新的 UTC 微秒时间。</li>
     */
    private Instant updateTime;
}
