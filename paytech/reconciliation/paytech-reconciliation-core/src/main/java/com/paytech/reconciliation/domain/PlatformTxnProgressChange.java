package com.paytech.reconciliation.domain;

import java.math.BigDecimal;

import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;

/**
 * 一笔平台流水对账进度的目标值（匹配与对平批量条件更新，按 record 的主键与版本比较）
 *
 * @param record 已读取的平台流水，更新成功后同步
 * @param matchedAmount 新的已匹配金额
 * @param actualFee 新的实际渠道费累计
 * @param settledAmount 新的已结清金额
 * @param status 新状态
 */
public record PlatformTxnProgressChange(PlatformTxn record, BigDecimal matchedAmount, BigDecimal actualFee,
    BigDecimal settledAmount, PlatformTxnStatus status) {
}
