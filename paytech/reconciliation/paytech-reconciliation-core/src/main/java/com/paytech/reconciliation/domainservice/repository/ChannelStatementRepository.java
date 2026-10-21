package com.paytech.reconciliation.domainservice.repository;

import com.paytech.reconciliation.domain.view.ReconSummaryView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;

/** t_channel_statement（渠道账单）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface ChannelStatementRepository {
    /**
     * 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param record 已准备主键、初始状态与时间的新记录
     */
    void insert(ChannelStatement record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    ChannelStatement findById(Long id);

    /**
     * 按主键集合读取；空集合不查询。
     * 
     * @param ids 主键集合
     * @return 存在的记录，按主键升序
     */
    List<ChannelStatement> findByIds(List<Long> ids);

    /**
     * 读取同一清算账户 + 批次号的全部导入版本（计算 revision、检查未作废版本）。
     * 
     * @param clearingAccountId 清算账户主键
     * @param channelBatchRef 渠道批次号
     * @return 按 revision 升序
     */
    List<ChannelStatement> listByBatch(Long clearingAccountId, String channelBatchRef);

    /**
     * 按可选条件分页读取（清算账户、批次号、状态、结算日区间）；null 条件不过滤。
     * 
     * @param clearingAccountId 可选条件
     * @param channelBatchRef 可选条件
     * @param status 可选条件
     * @param settlementDateFrom 可选条件
     * @param settlementDateTo 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<ChannelStatement> findPage(Long clearingAccountId, String channelBatchRef, ChannelStatementStatus status,
        LocalDate settlementDateFrom, LocalDate settlementDateTo, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param clearingAccountId 可选条件
     * @param channelBatchRef 可选条件
     * @param status 可选条件
     * @param settlementDateFrom 可选条件
     * @param settlementDateTo 可选条件
     * @return 总数
     */
    long countPage(Long clearingAccountId, String channelBatchRef, ChannelStatementStatus status,
        LocalDate settlementDateFrom, LocalDate settlementDateTo);

    /**
     * 分段追加明细行后，在 RECEIVING 状态下按版本更新已接收行数。
     * 
     * @param record 已读取的账单
     * @param importedLineCount 新的已接收行数
     */
    void updateImportedLineCount(ChannelStatement record, Integer importedLineCount);

    /**
     * 按版本与原状态推进状态（完成导入、拒绝、作废、对平）；拒绝原因与对平时间按目标状态传入，其他情况传原值或 null。
     * 
     * @param record 已读取的账单
     * @param status 目标状态
     * @param rejectReason 整单拒绝原因
     * @param reconciledTime 对平时间
     */
    void changeStatus(ChannelStatement record, ChannelStatementStatus status, String rejectReason,
        Instant reconciledTime);

    /**
     * 银行确认或归类结清后按版本更新到账确认金额，满额时转 CASH_CONFIRMED。
     * 
     * @param record 已读取的账单
     * @param cashConfirmedAmount 新的到账确认金额
     * @param status 新状态
     */
    void updateCashConfirmed(ChannelStatement record, BigDecimal cashConfirmedAmount, ChannelStatementStatus status);

    /**
     * 按状态汇总在途渠道账单（MATCHING、RECONCILED）。
     *
     * @param entityNo 可选经营主体编号；为空汇总全部清算账户
     * @param currency 币种
     * @return 每个状态一行
     */
    List<ReconSummaryView.StatementTotal> sumInTransit(String entityNo, String currency);
}
