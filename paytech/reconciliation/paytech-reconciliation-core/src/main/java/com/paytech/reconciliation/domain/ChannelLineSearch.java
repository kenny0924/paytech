package com.paytech.reconciliation.domain;

import java.time.LocalDate;
import java.util.List;

import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.LineMatchStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * 跨账单查询渠道账单行的条件（渠道流水页）
 * <li>{@link #getExcludedStatementStatuses} 统一决定账单状态口径：未指定状态时排除 {@link #DEFAULT_EXCLUDED_STATEMENT_STATUSES}，
 * 指定时只按该状态过滤。</li>
 * <li>null 条件不过滤；外部交易号类型与交易号均为精确匹配，交易日期区间两端都含。</li>
 */
@Getter
@Builder
public class ChannelLineSearch {
    /** 未指定账单状态时排除的状态：作废、被拒与导入中的账单行不是有效渠道流水。 */
    public static final List<ChannelStatementStatus> DEFAULT_EXCLUDED_STATEMENT_STATUSES = List.of(
        ChannelStatementStatus.VOIDED, ChannelStatementStatus.REJECTED, ChannelStatementStatus.RECEIVING);

    /** 清算账户 */
    private final Long clearingAccountId;

    /** 所属账单状态（指定时） */
    private final ChannelStatementStatus statementStatus;

    /** 匹配状态 */
    private final LineMatchStatus matchStatus;

    /** 行类型 */
    private final ChannelLineType lineType;

    /** 外部交易号类型 */
    private final String referenceType;

    /** 外部交易号 */
    private final String externalTransactionId;

    /** 交易日期起（含） */
    private final LocalDate transactionDateFrom;

    /** 交易日期止（含） */
    private final LocalDate transactionDateTo;

    /**
     * 排除的所属账单状态
     *
     * @return 未指定账单状态时为默认排除集合，否则为空列表
     */
    public List<ChannelStatementStatus> getExcludedStatementStatuses() {
        return statementStatus == null ? DEFAULT_EXCLUDED_STATEMENT_STATUSES : List.of();
    }
}
