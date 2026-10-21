package com.paytech.reconciliation.domainservice.repository;

import java.util.List;

import com.paytech.reconciliation.core.dal.dataobject.ClearingAccountMid;

/** t_clearing_account_mid（清算账户所含 MID）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface ClearingAccountMidRepository {
    /**
     * 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param record 已准备主键、初始状态与时间的新记录
     */
    void insert(ClearingAccountMid record);

    /**
     * 摘除 MID：删除本行；行数不为 1 抛并发冲突。
     * 
     * @param record 已读取的归属行
     */
    void delete(ClearingAccountMid record);

    /**
     * 按 MID + 币种读取归属（解析接口与重复归属检查）。
     * 
     * @param midId channel-connector MID 主键
     * @param currency 币种
     * @return 归属行；不存在为 null
     */
    ClearingAccountMid findByMidAndCurrency(Long midId, String currency);

    /**
     * 读取一个清算账户的全部 MID，按主键升序。
     * 
     * @param clearingAccountId 清算账户主键
     * @return 归属行
     */
    List<ClearingAccountMid> listByClearingAccount(Long clearingAccountId);

    /**
     * 按 MID 集合读取归属行（未归属 MID 计算）；空集合不查询。
     * 
     * @param midIds MID 主键集合
     * @return 归属行
     */
    List<ClearingAccountMid> listByMidIds(List<Long> midIds);
}
