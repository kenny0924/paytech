package com.paytech.reconciliation.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;

/** t_bank_match（银行匹配）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface BankMatchRepository {
    /**
     * 插入一条建议（PROPOSED）或确认的匹配（CONFIRMED）；流水级合计不超额由调用方在锁内校验。
     * 
     * @param record 已准备主键与时间的新匹配
     */
    void insert(BankMatch record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    BankMatch findById(Long id);

    /**
     * 读取一条银行流水的匹配，可按状态过滤。
     * 
     * @param bankStatementLineId 银行流水主键
     * @param status 可选状态
     * @return 按主键升序
     */
    List<BankMatch> listByLine(Long bankStatementLineId, BankMatchStatus status);

    /**
     * 读取一条预期银行流水的匹配，可按状态过滤。
     * 
     * @param expectedMovementId 预期银行流水主键
     * @param status 可选状态
     * @return 按主键升序
     */
    List<BankMatch> listByExpected(Long expectedMovementId, BankMatchStatus status);

    /**
     * 按可选条件分页读取（银行流水、预期银行流水、状态）；null 条件不过滤。
     * 
     * @param bankStatementLineId 可选条件
     * @param expectedMovementId 可选条件
     * @param status 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<BankMatch> findPage(Long bankStatementLineId, Long expectedMovementId, BankMatchStatus status, long offset,
        int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param bankStatementLineId 可选条件
     * @param expectedMovementId 可选条件
     * @param status 可选条件
     * @return 总数
     */
    long countPage(Long bankStatementLineId, Long expectedMovementId, BankMatchStatus status);

    /**
     * 按版本把 PROPOSED 建议转为 CONFIRMED 或 REJECTED。
     * 
     * @param record 已读取的建议
     * @param status 目标状态
     * @param operatorId 操作人
     * @param decisionTime 决定时间
     */
    void decide(BankMatch record, BankMatchStatus status, Long operatorId, Instant decisionTime);
}
