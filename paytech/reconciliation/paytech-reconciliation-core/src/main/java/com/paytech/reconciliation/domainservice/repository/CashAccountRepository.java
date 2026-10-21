package com.paytech.reconciliation.domainservice.repository;

import java.util.List;

import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;

/** t_cash_account（平台银行账户）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface CashAccountRepository {
    /**
     * 插入新银行账户（PENDING_VERIFICATION）；同主体 + 币种 + 用途的 ACTIVE 唯一性由复核通过时检查，库内无唯一约束。
     * 
     * @param record 已准备主键、Vault 令牌、末四位与时间的新记录
     */
    void insert(CashAccount record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    CashAccount findById(Long id);

    /**
     * 按主键集合读取；空集合不查询。
     * 
     * @param ids 主键集合
     * @return 存在的记录，按主键升序
     */
    List<CashAccount> findByIds(List<Long> ids);

    /**
     * 按主体 + 币种 + 用途 + 状态读取（当前账户查询、ACTIVE 唯一性检查）。
     * 
     * @param entityType 主体类型
     * @param entityId 主体标识
     * @param currency 币种
     * @param accountPurpose 资金用途
     * @param status 状态
     * @return 按主键升序
     */
    List<CashAccount> listByScope(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose accountPurpose, CashAccountStatus status);

    /**
     * 按可选条件分页读取（主体类型、主体标识、币种、用途、状态）；null 条件不过滤。
     * 
     * @param entityType 可选条件
     * @param entityId 可选条件
     * @param currency 可选条件
     * @param accountPurpose 可选条件
     * @param status 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<CashAccount> findPage(ReconEntityType entityType, String entityId, String currency,
        AccountPurpose accountPurpose, CashAccountStatus status, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param entityType 可选条件
     * @param entityId 可选条件
     * @param currency 可选条件
     * @param accountPurpose 可选条件
     * @param status 可选条件
     * @return 总数
     */
    long countPage(ReconEntityType entityType, String entityId, String currency, AccountPurpose accountPurpose,
        CashAccountStatus status);

    /**
     * 按版本与原状态更新状态与复核列（申请关闭、复核通过 / 拒绝）；不变的列传原值。
     * 
     * @param record 已读取的银行账户
     * @param status 目标状态
     * @param pendingAction 待复核变更，可为空
     * @param contentHash 最近一次提交复核内容 Hash
     * @param makerId 最近一次提交人
     * @param checkerId 复核人，可为空
     */
    void updateReview(CashAccount record, CashAccountStatus status, CashAccountPendingAction pendingAction,
        String contentHash, Long makerId, Long checkerId);
}
