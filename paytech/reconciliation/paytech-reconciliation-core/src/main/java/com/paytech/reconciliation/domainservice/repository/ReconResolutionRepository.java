package com.paytech.reconciliation.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;

/** t_recon_resolution（异常处置）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface ReconResolutionRepository {
    /**
     * 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。
     * 
     * @param record 已准备主键、初始状态与时间的新记录
     */
    void insert(ReconResolution record);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    ReconResolution findById(Long id);

    /**
     * 按主键集合读取；空集合不查询。
     * 
     * @param ids 主键集合
     * @return 存在的记录，按主键升序
     */
    List<ReconResolution> findByIds(List<Long> ids);

    /**
     * 读取一个异常的全部处置（处置历史、下一序号）。
     * 
     * @param exceptionId 异常主键
     * @return 按处置序号升序
     */
    List<ReconResolution> listByException(Long exceptionId);

    /**
     * 按可选条件分页读取（处置状态）；null 条件不过滤。
     * 
     * @param status 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<ReconResolution> findPage(ResolutionStatus status, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param status 可选条件
     * @return 总数
     */
    long countPage(ResolutionStatus status);

    /**
     * 按版本把 PENDING_CHECK 处置转为 APPLIED（批准即应用）或 REJECTED。
     * 
     * @param record 已读取的处置
     * @param status 目标状态
     * @param checkerId 复核人
     * @param checkerDecision 复核决定
     * @param approvedContentHash 复核人确认的内容 Hash
     * @param decisionTime 复核时间
     */
    void review(ReconResolution record, ResolutionStatus status, Long checkerId, CheckerDecision checkerDecision,
        String approvedContentHash, Instant decisionTime);
}
