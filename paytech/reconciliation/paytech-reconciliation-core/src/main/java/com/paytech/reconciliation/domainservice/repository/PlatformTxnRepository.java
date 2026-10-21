package com.paytech.reconciliation.domainservice.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.ExternalReference;
import com.paytech.reconciliation.domain.PlatformTxnProgressChange;

/** t_platform_txn（平台流水）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface PlatformTxnRepository {
    /**
     * 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param record 已准备主键、初始状态与时间的新记录
     */
    void insert(PlatformTxn record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    PlatformTxn findById(Long id);

    /**
     * 按主键集合读取；内部每 1,000 个主键一次查询，空集合不查询。
     * 
     * @param ids 主键集合
     * @return 存在的记录，按主键升序
     */
    List<PlatformTxn> findByIds(List<Long> ids);

    /**
     * 按来源 billing 事实读取（消费幂等）。
     * 
     * @param billingFactId billing 事实主键
     * @return 记录；不存在为 null
     */
    PlatformTxn findByBillingFactId(Long billingFactId);

    /**
     * 按清算账户 + 外部交易号读取应收与应付两个方向（账单行匹配）。
     * 
     * @param clearingAccountId 清算账户主键
     * @param referenceType 外部交易号类型
     * @param externalTransactionId 外部交易号
     * @return 至多两条，按主键升序
     */
    List<PlatformTxn> listByReference(Long clearingAccountId, String referenceType, String externalTransactionId);

    /**
     * 按清算账户 + 一组外部交易号读取应收与应付两个方向（匹配任务一次加载候选）；内部每 1,000 个交易号一次查询，空集合不查询。
     * 
     * @param clearingAccountId 清算账户主键
     * @param references 外部交易号集合
     * @return 按主键升序
     */
    List<PlatformTxn> listByReferences(Long clearingAccountId, Collection<ExternalReference> references);

    /**
     * 按可选条件分页读取（清算账户、渠道账单、状态、方向、外部交易号、业务时间区间 [from, to)）；null 条件不过滤。
     * <li>渠道账单条件只返回该账单行匹配到的平台流水（决定 I-02）。</li>
     * 
     * @param clearingAccountId 可选条件
     * @param statementId 可选条件
     * @param status 可选条件
     * @param direction 可选条件
     * @param externalTransactionId 可选条件
     * @param businessTimeFrom 可选条件
     * @param businessTimeTo 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<PlatformTxn> findPage(Long clearingAccountId, Long statementId, PlatformTxnStatus status, ClearingDirection direction,
        String externalTransactionId, Instant businessTimeFrom, Instant businessTimeTo, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param clearingAccountId 可选条件
     * @param statementId 可选条件
     * @param status 可选条件
     * @param direction 可选条件
     * @param externalTransactionId 可选条件
     * @param businessTimeFrom 可选条件
     * @param businessTimeTo 可选条件
     * @return 总数
     */
    long countPage(Long clearingAccountId, Long statementId, PlatformTxnStatus status, ClearingDirection direction,
        String externalTransactionId, Instant businessTimeFrom, Instant businessTimeTo);

    /**
     * 按版本更新对账进度（已匹配、实际费用、已结清金额与状态）；版本不符抛并发冲突。
     * 
     * @param record 已读取的平台流水
     * @param matchedAmount 新的已匹配金额
     * @param actualFee 新的实际渠道费累计
     * @param settledAmount 新的已结清金额
     * @param status 新状态
     */
    void updateProgress(PlatformTxn record, BigDecimal matchedAmount, BigDecimal actualFee, BigDecimal settledAmount,
        PlatformTxnStatus status);

    /**
     * 按版本批量更新多笔平台流水的对账进度（匹配与对平）；内部每 1,000 笔一条多行条件更新，任一笔版本不符抛并发冲突（调用方事务整体回滚）。
     * 成功后同步传入对象。
     * 
     * @param changes 每笔的目标值；空集合不更新
     */
    void updateProgresses(List<PlatformTxnProgressChange> changes);
}
