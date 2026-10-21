package com.paytech.reconciliation.domainservice.service;

import java.math.BigDecimal;
import java.util.List;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;
import com.paytech.reconciliation.domain.BankMatchContext;

/**
 * 银行层匹配与归类的保存（UC-R13、R15；设计 §6.4 不变量 5–6）
 * <li>事务传播为 REQUIRED；调用方已在锁内读取对象并校验（{@link BankMatchContext#check()}），本服务按版本条件更新保存。</li>
 * <li>渠道预期流水满额（已确认 + 已归类 = 预期）时所属渠道账单转 CASH_CONFIRMED；每条确认的渠道匹配同一事务登记一次
 * ChannelSettlementConfirmed。</li>
 */
public interface BankMatchService {

    /**
     * 插入系统建议（PROPOSED，AUTO）
     *
     * @param proposals 新建议
     */
    void propose(List<BankMatch> proposals);

    /**
     * 确认分配：建议转 CONFIRMED 或新建 CONFIRMED 匹配，累加预期流水与流水进度，推进渠道账单到账确认
     *
     * @param context 已校验的匹配上下文
     * @return 本次确认的匹配，顺序同分配
     */
    List<BankMatch> confirm(BankMatchContext context);

    /**
     * 拒绝建议（PROPOSED → REJECTED）
     *
     * @param proposal 已读取的建议
     * @param operatorId 操作人
     */
    void reject(BankMatch proposal, Long operatorId);

    /**
     * 预期银行流水的 CLASSIFY：已归类金额增加，满额 CONFIRMED，渠道账单随之推进
     *
     * @param movement 已读取的预期流水
     * @param channelStatement 渠道预期流水的来源账单；其他来源为空
     * @param amount 归类金额（带符号，等于剩余金额）
     */
    void classifyMovement(ExpectedBankMovement movement, ChannelStatement channelStatement, BigDecimal amount);

    /**
     * 银行流水的 CLASSIFY：流水转 MATCHED（已匹配金额不变，归类不是匹配）
     *
     * @param line 已读取的银行流水
     */
    void classifyLine(BankStatementLine line);
}
