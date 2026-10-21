package com.paytech.reconciliation.domainservice.service;

import java.util.List;

import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.domain.VoidChannelStatementContext;

/**
 * 渠道账单分段导入与作废的本地保存（UC-R06、R08，technique D-01）
 * <li>调用方（Processor）已在账单锁内完成状态、上传人与内容校验；这里按版本与原状态条件写入，冲突抛 409。</li>
 */
public interface ChannelStatementService {

    /**
     * 开始导入：插入 RECEIVING 账单头
     *
     * @param statement 已准备主键、版本号与时间的账单头；清算账户 + 批次号 + 版本唯一键冲突抛 409
     */
    void start(ChannelStatement statement);

    /**
     * 追加一段新行并更新已接收行数
     *
     * @param statement 已读取的 RECEIVING 账单
     * @param lines 本段中尚未接收的新行（可为空，此时不写入）
     */
    void appendLines(ChannelStatement statement, List<ChannelStatementLine> lines);

    /**
     * 完成导入校验通过：RECEIVING → MATCHING，同一事务登记 CHANNEL_STATEMENT_MATCH（来源 IMPORT）
     *
     * @param statement 已读取的 RECEIVING 账单
     */
    void accept(ChannelStatement statement);

    /**
     * 完成导入校验不通过：RECEIVING → REJECTED，保留账单头与汇总原因
     *
     * @param statement 已读取的 RECEIVING 账单
     * @param rejectReason 汇总原因（≤ 1000 字符）
     */
    void reject(ChannelStatement statement, String rejectReason);

    /**
     * 等待期满仍未匹配的交易行转为 EXCEPTION（异常开立由 Processor 在同一事务组合）
     *
     * @param line 已读取的 UNMATCHED 行；版本冲突抛 409
     */
    void markLineException(ChannelStatementLine line);

    /**
     * 作废：释放行匹配与实际费用、回退平台流水进度、账单转 VOIDED（本账单开放异常的自动结案由 Processor 在同一事务组合）
     *
     * @param context 作废上下文
     */
    void voidStatement(VoidChannelStatementContext context);
}
