package com.paytech.reconciliation.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;

/**
 * 一行账单行匹配结果的目标值（匹配任务批量条件更新，按 record 的主键与版本比较）
 *
 * @param record 已读取的行，更新成功后同步
 * @param platformTxnId 匹配到的平台流水；释放或开异常时为空
 * @param matchMethod 匹配方式
 * @param matchedTime 匹配时间
 * @param actualFee 确认的实际渠道费
 * @param resolutionId 已应用的归类处置
 * @param matchStatus 匹配状态
 */
public record ChannelLineMatchChange(ChannelStatementLine record, Long platformTxnId, MatchMethod matchMethod,
    Instant matchedTime, BigDecimal actualFee, Long resolutionId, LineMatchStatus matchStatus) {
}
