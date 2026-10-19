package com.paytech.commercial.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.commercial.core.dal.dataobject.AgreementCapabilityLimit;

/** t_agreement_capability_limit 的本地仓储；约束随开通项整体替换，不单独审计。 */
public interface AgreementCapabilityLimitRepository {
    /**
     * 批量插入约束行；空列表不访问数据库，影响行数与列表大小不符时抛出系统异常，唯一键冲突原样抛出。
     *
     * @param limits 已准备 ID 与全部字段的新行
     */
    void insertAll(List<AgreementCapabilityLimit> limits);

    /**
     * 按主键读取。
     *
     * @param agreementCapabilityLimitId 约束行 ID
     * @return 约束行，不存在为 null
     */
    AgreementCapabilityLimit findById(long agreementCapabilityLimitId);

    /**
     * 读取一个开通项的全部约束。
     *
     * @param agreementCapabilityId 开通项 ID
     * @return 按主键升序的约束行
     */
    List<AgreementCapabilityLimit> findByAgreementCapabilityId(long agreementCapabilityId);

    /**
     * 读取一组开通项的全部约束；空集合不访问数据库。
     *
     * @param agreementCapabilityIds 开通项 ID
     * @return 按主键升序的约束行
     */
    List<AgreementCapabilityLimit> findByAgreementCapabilityIds(Collection<Long> agreementCapabilityIds);

    /**
     * 删除一个开通项的全部约束，用于整体替换。
     *
     * @param agreementCapabilityId 开通项 ID
     * @return 删除行数
     */
    int deleteByAgreementCapabilityId(long agreementCapabilityId);
}
