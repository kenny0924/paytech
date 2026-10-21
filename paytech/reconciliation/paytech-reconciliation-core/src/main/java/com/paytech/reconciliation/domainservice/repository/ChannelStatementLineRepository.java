package com.paytech.reconciliation.domainservice.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.domain.ChannelLineMatchChange;
import com.paytech.reconciliation.domain.ChannelLineSearch;
import com.paytech.reconciliation.domain.ExternalReference;

/** t_channel_statement_line（渠道账单行）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface ChannelStatementLineRepository {
    /**
     * 批量插入一段明细行（每段 ≤ 100 行）；行号冲突转为并发冲突，调用方先按行号查询实现同号同内容幂等。
     * 
     * @param records 同一账单的新明细行
     */
    void insertBatch(List<ChannelStatementLine> records);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    ChannelStatementLine findById(Long id);

    /**
     * 按账单与行号集合读取（分段追加的同号幂等比较）；空集合不查询。
     * 
     * @param statementId 账单主键
     * @param lineNos 行号集合
     * @return 已存在的行，按行号升序
     */
    List<ChannelStatementLine> listByStatementAndLineNos(Long statementId, List<Integer> lineNos);

    /**
     * 读取一张账单的全部行（完成导入校验、逐行匹配、对平门禁）；单张账单至多 20,000 行。
     * 
     * @param statementId 账单主键
     * @return 按行号升序
     */
    List<ChannelStatementLine> listByStatement(Long statementId);

    /**
     * 按清算账户 + 外部交易号读取全部账单的行（跨账单查重与同交易号 FEE 行）。
     * 
     * @param clearingAccountId 清算账户主键
     * @param referenceType 外部交易号类型
     * @param externalTransactionId 外部交易号
     * @return 按主键升序
     */
    List<ChannelStatementLine> listByReference(Long clearingAccountId, String referenceType,
        String externalTransactionId);

    /**
     * 按清算账户 + 一组外部交易号读取全部账单的行（匹配任务一次加载跨账单查重的候选）；内部每 1,000 个交易号一次查询，空集合不查询。
     * 
     * @param clearingAccountId 清算账户主键
     * @param references 外部交易号集合
     * @return 按主键升序
     */
    List<ChannelStatementLine> listByReferences(Long clearingAccountId, Collection<ExternalReference> references);

    /**
     * 读取匹配到某笔平台流水的全部行。
     * 
     * @param platformTxnId 平台流水主键
     * @return 按主键升序
     */
    List<ChannelStatementLine> listByPlatformTxn(Long platformTxnId);

    /**
     * 读取一个清算账户下接收时间早于给定时刻仍为 UNMATCHED 的行（等待期满任务）。
     * 
     * @param clearingAccountId 清算账户主键
     * @param createdBefore 接收时间上限（不含）
     * @param limit 本批上限
     * @return 按接收时间、主键升序
     */
    List<ChannelStatementLine> listUnmatchedCreatedBefore(Long clearingAccountId, Instant createdBefore, int limit);

    /**
     * 读取一个清算账户下 MATCHING 账单中、接收时间早于给定时刻仍为 UNMATCHED 的交易行（等待期满任务）
     * <li>只含 PAYMENT、REFUND、CHARGEBACK；带交易号的 FEE 行随交易行处理，ADJUSTMENT 与不带交易号的 FEE 行由匹配任务直接开异常。</li>
     * 
     * @param clearingAccountId 清算账户主键
     * @param createdBefore 接收时间上限（不含）
     * @param limit 本批上限
     * @return 按接收时间、主键升序
     */
    List<ChannelStatementLine> listWaitExpired(Long clearingAccountId, Instant createdBefore, int limit);

    /**
     * 按可选条件分页读取（账单（必填）、匹配状态、行类型）；null 条件不过滤。
     * 
     * @param statementId 可选条件
     * @param matchStatus 可选条件
     * @param lineType 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<ChannelStatementLine> findPage(Long statementId, LineMatchStatus matchStatus, ChannelLineType lineType,
        long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param statementId 可选条件
     * @param matchStatus 可选条件
     * @param lineType 可选条件
     * @return 总数
     */
    long countPage(Long statementId, LineMatchStatus matchStatus, ChannelLineType lineType);

    /**
     * 跨账单按条件分页读取（渠道流水页），交易日期降序（无交易日期在最后）、主键降序。
     * 
     * @param criteria 查询条件（含账单状态口径）
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<ChannelStatementLine> searchPage(ChannelLineSearch criteria, long offset, int limit);

    /**
     * 与 searchPage 相同条件的总数。
     * 
     * @param criteria 查询条件
     * @return 总数
     */
    long countSearch(ChannelLineSearch criteria);

    /**
     * 按版本写入一行的匹配结果；释放匹配时把引用列传 null、状态传 UNMATCHED。
     * 
     * @param record 已读取的行
     * @param platformTxnId 匹配到的平台流水
     * @param matchMethod 匹配方式
     * @param matchedTime 匹配时间
     * @param actualFee 确认的实际渠道费
     * @param resolutionId 已应用的归类处置
     * @param matchStatus 匹配状态
     */
    void updateMatch(ChannelStatementLine record, Long platformTxnId, MatchMethod matchMethod, Instant matchedTime,
        BigDecimal actualFee, Long resolutionId, LineMatchStatus matchStatus);

    /**
     * 按版本批量写入多行的匹配结果（匹配任务）；内部每 1,000 行一条多行条件更新，任一行版本不符抛并发冲突（调用方事务整体回滚）。
     * 成功后同步传入对象。
     * 
     * @param changes 每行的目标值；空集合不更新
     */
    void updateMatches(List<ChannelLineMatchChange> changes);
}
