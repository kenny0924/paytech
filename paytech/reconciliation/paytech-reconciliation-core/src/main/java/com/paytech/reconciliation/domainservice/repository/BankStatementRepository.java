package com.paytech.reconciliation.domainservice.repository;

import java.util.List;

import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;

/** t_bank_statement（银行对账单头）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface BankStatementRepository {
    /**
     * 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param record 已准备主键、初始状态与时间的新记录
     */
    void insert(BankStatement record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    BankStatement findById(Long id);

    /**
     * 按银行账户 + 对账单编号读取。
     * 
     * @param cashAccountId 银行账户主键
     * @param statementRef 对账单编号
     * @return 记录；不存在为 null
     */
    BankStatement findByRef(Long cashAccountId, String statementRef);

    /**
     * 读取同一银行账户期间结束日最晚的已导入对账单（连续性检查）。
     * 
     * @param cashAccountId 银行账户主键
     * @return 记录；没有为 null
     */
    BankStatement findLatestImported(Long cashAccountId);

    /**
     * 按可选条件分页读取（银行账户、状态）；null 条件不过滤。
     * 
     * @param cashAccountId 可选条件
     * @param status 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<BankStatement> findPage(Long cashAccountId, BankStatementStatus status, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param cashAccountId 可选条件
     * @param status 可选条件
     * @return 总数
     */
    long countPage(Long cashAccountId, BankStatementStatus status);

    /**
     * 分段追加后在 RECEIVING 状态下按版本更新已接收行数、重复行数与待拒绝的逐行原因。
     *
     * @param record 已读取的对账单
     * @param importedLineCount 新的已接收行数
     * @param duplicateLineCount 新的重复行数（银行交易号已入库、无法入库的行）
     * @param rejectReason 接收中记下的逐行重复原因，完成导入时整单拒绝；没有为空
     */
    void updateImportProgress(BankStatement record, Integer importedLineCount, Integer duplicateLineCount,
        String rejectReason);

    /**
     * 同编号更正重传：已拒绝的对账单按版本重置为 RECEIVING，换成新的头、文件信息、声明行数与上传人，清空计数与拒绝原因。
     *
     * @param record 已读取的 REJECTED 对账单
     * @param replacement 新请求构造的对账单头（只取头部与文件字段）
     */
    void restart(BankStatement record, BankStatement replacement);

    /**
     * 完成导入时按版本与原状态转 IMPORTED 或 REJECTED。
     * 
     * @param record 已读取的对账单
     * @param status 目标状态
     * @param rejectReason 整单拒绝原因，可为空
     */
    void changeStatus(BankStatement record, BankStatementStatus status, String rejectReason);
}
