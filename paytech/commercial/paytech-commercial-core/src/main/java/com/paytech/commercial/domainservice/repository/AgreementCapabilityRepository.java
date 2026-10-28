package com.paytech.commercial.domainservice.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
import com.paytech.commercial.api.enums.AgreementCapabilityStatus;
import com.paytech.commercial.core.dal.dataobject.AgreementCapability;

/** t_agreement_capability 的本地仓储；不负责约束行、其他表的加载或事务。 */
public interface AgreementCapabilityRepository {
    /**
     * 插入开通项；影响行数不为 1 时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象，使后续以 update_time 为条件的更新可命中。</li>
     *
     * @param capability 已准备 ID 与全部字段的新行
     */
    void insert(AgreementCapability capability);

    /**
     * 按主键读取。
     *
     * @param agreementCapabilityId 开通项 ID
     * @return 开通项，不存在为 null
     */
    AgreementCapability findById(long agreementCapabilityId);

    /**
     * 按唯一键读取。
     *
     * @param agreementId 合同 ID
     * @param entityNo 准确 OE
     * @param capabilityCode 可开通项代码
     * @return 开通项，不存在为 null
     */
    AgreementCapability findByUniqueKey(long agreementId, String entityNo, String capabilityCode);

    /**
     * 读取一份合同的全部开通项。
     *
     * @param agreementId 合同 ID
     * @return 按主键升序的开通项
     */
    List<AgreementCapability> findByAgreementId(long agreementId);

    /**
     * 读取一组合同的全部开通项；空集合不访问数据库。
     *
     * @param agreementIds 合同 ID
     * @return 按主键升序的开通项
     */
    List<AgreementCapability> findByAgreementIds(Collection<Long> agreementIds);

    /**
     * 以对象当前成员资格与执行状态为条件改写二者；未命中抛出并发冲突，成功后同步原对象的两个状态与更新时间。
     *
     * @param capability 已读取的开通项
     * @param status 目标成员资格
     * @param executionStatus 目标执行状态；DISABLED 时为空
     * @param now 本次更新时间
     */
    void updateMembership(AgreementCapability capability, AgreementCapabilityStatus status,
        AgreementCapabilityExecutionStatus executionStatus, Instant now);

    /**
     * 以成员资格 ACTIVE 与对象当前执行状态为条件迁移执行状态；未命中抛出并发冲突，成功后同步原对象的执行状态与更新时间。
     *
     * @param capability 已读取的开通项
     * @param executionStatus 目标执行状态
     * @param now 本次更新时间
     */
    void updateExecutionStatus(AgreementCapability capability, AgreementCapabilityExecutionStatus executionStatus,
        Instant now);

    /**
     * 有执行状态属于给定集合的开通项、落在本分片的合同 ID，按 ID 升序从游标之后取一页（重查 Job 使用）。
     *
     * @param executionStatuses 执行状态集合，非空
     * @param afterAgreementId 游标：只取大于该值的合同 ID，首页为 0
     * @param shardIndex 分片序号，0 起
     * @param shardTotal 分片总数，至少 1
     * @param limit 页大小
     * @return 合同 ID 升序
     */
    List<Long> findAgreementIdsByExecutionStatus(Collection<AgreementCapabilityExecutionStatus> executionStatuses,
        long afterAgreementId, int shardIndex, int shardTotal, int limit);

    /**
     * 在某 OE 上有开通项的合同 ID（资格事件按 OE 定位合同）。
     *
     * @param entityNo 准确 OE
     * @return 合同 ID 升序
     */
    List<Long> findAgreementIdsByEntityNo(String entityNo);

    /**
     * 以对象当前 update_time 为条件改写生效区间与保证金条款；未命中抛出并发冲突，成功后同步原对象。
     *
     * @param capability 已读取的开通项
     * @param effectiveFrom 新的生效起点（含）
     * @param effectiveTo 新的生效终点（不含），可空
     * @param rollingReserveRate 滚动保证金比例，可空，与天数成组
     * @param rollingReserveDays 滚动保证金天数，可空，与比例成组
     * @param require3ds 是否要求 3DS
     * @param now 本次更新时间
     */
    void updateTerms(AgreementCapability capability, Instant effectiveFrom, Instant effectiveTo,
        BigDecimal rollingReserveRate, Integer rollingReserveDays, Boolean require3ds,
        Instant now);
}
