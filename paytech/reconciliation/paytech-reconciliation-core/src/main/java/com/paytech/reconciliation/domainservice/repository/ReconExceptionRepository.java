package com.paytech.reconciliation.domainservice.repository;

import com.paytech.reconciliation.domain.view.ReconSummaryView;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;

/** t_recon_exception（对账异常）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface ReconExceptionRepository {
    /**
     * 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param record 已准备主键、初始状态与时间的新记录
     */
    void insert(ReconException record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    ReconException findById(Long id);

    /**
     * 读取同一对象同一类型的未结异常（OPEN、UNDER_REVIEW）。
     * 
     * @param sourceType 挂靠对象类型
     * @param sourceId 挂靠对象 ID
     * @param exceptionType 异常类型
     * @return 未结异常；没有为 null
     */
    ReconException findActive(ReconExceptionSourceType sourceType, Long sourceId, ReconExceptionType exceptionType);

    /**
     * 读取同一对象同一类型最近开立的一条异常（任意状态）
     * <li>匹配任务据此判断：重复结算是否已被处置接受（RESOLVED）、成本偏差是否已开过（作废自动结案的不算）。</li>
     * 
     * @param sourceType 挂靠对象类型
     * @param sourceId 挂靠对象 ID
     * @param exceptionType 异常类型
     * @return 最近一条；没有为 null
     */
    ReconException findLatestBySource(ReconExceptionSourceType sourceType, Long sourceId,
        ReconExceptionType exceptionType);

    /**
     * 按主键批量插入（匹配任务一次开立多条）；内部每 1,000 条一条多行插入，业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param records 已准备主键、初始状态与时间的新记录；空集合不插入
     */
    void insertAll(List<ReconException> records);

    /**
     * 读取一组同类型对象上同一类型的未结异常（OPEN、UNDER_REVIEW）；内部每 1,000 个对象一次查询，空集合不查询。
     * 
     * @param sourceType 挂靠对象类型
     * @param sourceIds 挂靠对象 ID 集合
     * @param exceptionType 异常类型
     * @return 未结异常，每个对象至多一条
     */
    List<ReconException> listActiveBySources(ReconExceptionSourceType sourceType, Collection<Long> sourceIds,
        ReconExceptionType exceptionType);

    /**
     * 读取一组同类型对象上同一类型各自最近开立的一条异常（任意状态，口径同 {@link #findLatestBySource}）；内部每 1,000 个对象一次查询。
     * 
     * @param sourceType 挂靠对象类型
     * @param sourceIds 挂靠对象 ID 集合
     * @param exceptionType 异常类型
     * @return 每个有异常的对象一条
     */
    List<ReconException> listLatestBySources(ReconExceptionSourceType sourceType, Collection<Long> sourceIds,
        ReconExceptionType exceptionType);

    /**
     * 读取一张渠道账单的未结异常（对平门禁、作废时自动结案）。
     * 
     * @param statementId 渠道账单主键
     * @return 按主键升序
     */
    List<ReconException> listActiveByStatement(Long statementId);

    /**
     * 按可选条件分页读取（状态集合、异常类型、对账层、清算账户、银行账户、渠道账单）；null 条件不过滤。
     * 
     * @param statuses 可选条件
     * @param exceptionType 可选条件
     * @param reconLayer 可选条件
     * @param clearingAccountId 可选条件
     * @param cashAccountId 可选条件
     * @param statementId 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<ReconException> findPage(List<ReconExceptionStatus> statuses, ReconExceptionType exceptionType,
        ReconLayer reconLayer, Long clearingAccountId, Long cashAccountId, Long statementId, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param statuses 可选条件
     * @param exceptionType 可选条件
     * @param reconLayer 可选条件
     * @param clearingAccountId 可选条件
     * @param cashAccountId 可选条件
     * @param statementId 可选条件
     * @return 总数
     */
    long countPage(List<ReconExceptionStatus> statuses, ReconExceptionType exceptionType, ReconLayer reconLayer,
        Long clearingAccountId, Long cashAccountId, Long statementId);

    /**
     * 按版本与原状态推进状态；OPEN、UNDER_REVIEW 占用活动槽位 1，RESOLVED、AUTO_CLOSED 置空并写结案时间。
     * 
     * @param record 已读取的异常
     * @param status 目标状态
     * @param closedTime 结案时间；未结案传 null
     */
    void changeStatus(ReconException record, ReconExceptionStatus status, Instant closedTime);

    /**
     * 按版本与原状态把多条异常推进到同一状态（匹配任务自动结案）；内部每 1,000 条一条条件更新，任一条不符抛并发冲突。
     * 成功后同步传入对象；槽位与结案时间口径同 {@link #changeStatus}。
     * 
     * @param records 已读取的异常；空集合不更新
     * @param status 目标状态
     * @param closedTime 结案时间；未结案传 null
     */
    void changeStatusAll(List<ReconException> records, ReconExceptionStatus status, Instant closedTime);

    /**
     * 按账龄段汇总未结异常（OPEN、UNDER_REVIEW）：0–3、4–7、8–30、31 天及以上
     *
     * @param entityNo 可选经营主体编号；为空汇总全部主体
     * @param currency 币种
     * @param now 计算账龄的当前时间
     * @return 有异常的账龄段各一行
     */
    List<ReconSummaryView.ExceptionAgeTotal> sumOpenByAge(String entityNo, String currency, Instant now);
}
