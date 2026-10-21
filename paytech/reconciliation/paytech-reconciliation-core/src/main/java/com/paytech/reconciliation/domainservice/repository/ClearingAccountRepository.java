package com.paytech.reconciliation.domainservice.repository;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.core.dal.dataobject.ClearingAccount;

/** t_clearing_account（渠道清算账户）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface ClearingAccountRepository {
    /**
     * 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param record 已准备主键、初始状态与时间的新记录
     */
    void insert(ClearingAccount record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    ClearingAccount findById(Long id);

    /**
     * 按主键集合读取；空集合不查询。
     * 
     * @param ids 主键集合
     * @return 存在的记录，按主键升序
     */
    List<ClearingAccount> findByIds(List<Long> ids);

    /**
     * 按清算账户编码读取（渠道账单文件用编码指明清算账户）。
     * 
     * @param clearingAccountCode 编码
     * @return 记录；不存在为 null
     */
    ClearingAccount findByCode(String clearingAccountCode);

    /**
     * 按可选条件分页读取（经营主体、渠道、币种、状态）；null 条件不过滤。
     * 
     * @param entityNo 可选条件
     * @param channelCode 可选条件
     * @param currency 可选条件
     * @param status 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<ClearingAccount> findPage(String entityNo, String channelCode, String currency,
        ClearingAccountStatus status, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param entityNo 可选条件
     * @param channelCode 可选条件
     * @param currency 可选条件
     * @param status 可选条件
     * @return 总数
     */
    long countPage(String entityNo, String channelCode, String currency, ClearingAccountStatus status);

    /**
     * 按版本更新三项配置与到账银行账户；版本不符抛并发冲突，不修改传入对象。
     * 
     * @param record 已读取的清算账户
     * @param unmatchedWaitDays 未匹配等待天数
     * @param costToleranceAmount 成本容差
     * @param cashGraceDays 到账宽限天数
     * @param settlementCashAccountId 到账银行账户，可为空
     */
    void updateSettings(ClearingAccount record, Integer unmatchedWaitDays, BigDecimal costToleranceAmount,
        Integer cashGraceDays, Long settlementCashAccountId);

    /**
     * 按版本与原状态启用或停用；冲突抛并发冲突。
     * 
     * @param record 已读取的清算账户
     * @param status 目标状态
     */
    void changeStatus(ClearingAccount record, ClearingAccountStatus status);

    /**
     * 只推进版本：挂入或摘除 MID 时与 t_clearing_account_mid 写入同一事务，按版本串行化同一清算账户的 MID 变更。
     * 
     * @param record 已读取的清算账户
     */
    void increaseVersion(ClearingAccount record);
}
