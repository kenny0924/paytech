package com.paytech.reconciliation.domainservice.repository;

import java.math.BigDecimal;
import java.util.List;

import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.core.dal.dataobject.ExpectedBankMovement;

/** t_expected_bank_movement（预期银行流水）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface ExpectedBankMovementRepository {
    /**
     * 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param record 已准备主键、初始状态与时间的新记录
     */
    void insert(ExpectedBankMovement record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    ExpectedBankMovement findById(Long id);

    /**
     * 按主键集合读取；空集合不查询。
     * 
     * @param ids 主键集合
     * @return 存在的记录，按主键升序
     */
    List<ExpectedBankMovement> findByIds(List<Long> ids);

    /**
     * 按预期来源读取（渠道账单对平生成的预期流水）。
     * 
     * @param movementType 预期来源
     * @param sourceId 来源 ID
     * @return 记录；不存在为 null
     */
    ExpectedBankMovement findBySource(ExpectedMovementType movementType, Long sourceId);

    /**
     * 读取银行账户下 OPEN / PARTIALLY_CONFIRMED 的预期流水（匹配建议）。
     * 
     * @param cashAccountId 银行账户主键
     * @return 按预期日、主键升序
     */
    List<ExpectedBankMovement> listOpen(Long cashAccountId);

    /**
     * 按可选条件分页读取（银行账户、来源类型、来源 ID、状态）；null 条件不过滤。
     * 
     * @param cashAccountId 可选条件
     * @param movementType 可选条件
     * @param sourceId 可选条件
     * @param status 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<ExpectedBankMovement> findPage(Long cashAccountId, ExpectedMovementType movementType, Long sourceId,
        ExpectedMovementStatus status, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param cashAccountId 可选条件
     * @param movementType 可选条件
     * @param sourceId 可选条件
     * @param status 可选条件
     * @return 总数
     */
    long countPage(Long cashAccountId, ExpectedMovementType movementType, Long sourceId, ExpectedMovementStatus status);

    /**
     * 按版本更新已确认金额、归类金额与状态。
     * 
     * @param record 已读取的预期流水
     * @param confirmedAmount 新的已确认金额
     * @param classifiedAmount 新的归类金额（带符号）
     * @param status 新状态
     */
    void updateProgress(ExpectedBankMovement record, BigDecimal confirmedAmount, BigDecimal classifiedAmount,
        ExpectedMovementStatus status);
}
