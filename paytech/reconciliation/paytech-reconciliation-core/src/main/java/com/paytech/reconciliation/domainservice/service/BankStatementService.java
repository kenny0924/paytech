package com.paytech.reconciliation.domainservice.service;

import java.util.List;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;

/**
 * 银行对账单分段导入的保存（UC-R11，technique D-01）
 * <li>事务传播为 REQUIRED；完成导入时与 BANK_STATEMENT_GAP 开立在调用方的同一事务内提交。</li>
 */
public interface BankStatementService {

    /**
     * 插入 RECEIVING 对账单头
     *
     * @param statement 已准备主键与初始值的新对账单
     */
    void start(BankStatement statement);

    /**
     * 同编号更正重传：已拒绝的对账单头重置为 RECEIVING（其流水已在拒绝时删除）
     *
     * @param statement 已读取的 REJECTED 对账单
     * @param replacement 新请求构造的对账单头
     */
    void restart(BankStatement statement, BankStatement replacement);

    /**
     * 插入一段新流水并更新已接收行数、重复行数与待拒绝原因
     *
     * @param statement 已读取的 RECEIVING 对账单
     * @param lines 需要插入的新流水
     * @param duplicateCount 本段新记下的重复交易号行数
     * @param pendingReasons 记下的逐行重复原因（含本段）；没有为空
     */
    void appendLines(BankStatement statement, List<BankStatementLine> lines, int duplicateCount,
        String pendingReasons);

    /**
     * RECEIVING → IMPORTED，并登记该银行账户的匹配建议任务
     *
     * @param statement 已读取的 RECEIVING 对账单
     */
    void accept(BankStatement statement);

    /**
     * RECEIVING → REJECTED，删除已接收的流水（释放银行交易号，便于更正后重新导入）
     *
     * @param statement 已读取的 RECEIVING 对账单
     * @param rejectReason 汇总原因
     */
    void reject(BankStatement statement, String rejectReason);
}
