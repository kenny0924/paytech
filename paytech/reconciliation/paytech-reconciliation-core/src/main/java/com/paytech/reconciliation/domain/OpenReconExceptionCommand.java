package com.paytech.reconciliation.domain;

import java.math.BigDecimal;

import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionType;

import lombok.Builder;
import lombok.Getter;

/**
 * 开立对账异常的命令（{@code ReconExceptionService#open}）
 * <li>对账层与是否阻塞由异常类型派生（{@link ReconExceptionTypes}），调用方不传。</li>
 * <li>主体、币种与差异金额由调用方按来源父链确定，不得人工改挂（设计 §6.4 不变量 7）。</li>
 */
@Getter
@Builder
public class OpenReconExceptionCommand {
    /** 异常类型 */
    private final ReconExceptionType exceptionType;

    /** 挂靠的本库对象类型 */
    private final ReconExceptionSourceType sourceType;

    /** 挂靠对象 ID */
    private final Long sourceId;

    /**
     * 交易层异常所属渠道账单
     * <li>对平门禁按它查询；无关时为空。</li>
     */
    private final Long statementId;

    /** 来源父链的清算账户；银行层为空 */
    private final Long clearingAccountId;

    /** 来源父链的银行账户；交易层为空 */
    private final Long cashAccountId;

    /** 主体类型 */
    private final ReconEntityType entityType;

    /** 主体标识：经营主体为 entityNo，财务主体为 ledger 主键十进制字符串 */
    private final String entityId;

    /** 币种 */
    private final String currency;

    /** 系统算出的待处置差异（带符号）；归类处置金额必须与之相等 */
    private final BigDecimal differenceAmount;

    /**
     * 系统生成的说明
     * <li>超过 1000 字符时截断。</li>
     */
    private final String detail;

    /** 人工开异常的 Platform 账户 ID；系统开的为空 */
    private final Long openedBy;
}
