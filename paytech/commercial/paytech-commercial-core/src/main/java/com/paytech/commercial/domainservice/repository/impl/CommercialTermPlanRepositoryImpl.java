package com.paytech.commercial.domainservice.repository.impl;

import static com.paytech.commercial.domainservice.repository.impl.PersistenceTimes.micros;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.core.dal.mapper.CommercialTermPlanMapper;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.CommercialTermPlanListQuery;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 只通过 CommercialTermPlanMapper 读取 t_commercial_term_plan。 */
@AllArgsConstructor
public class CommercialTermPlanRepositoryImpl implements CommercialTermPlanRepository {
    private final CommercialTermPlanMapper mapper;

    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_plan 记录，保留历史和未生效事实供业务判断。 */
    @Override
    public List<CheckoutRecords.CommercialTermPlan> findForCheckout(CommercialCheckoutScope scope) {
        return List.copyOf(mapper.findForCheckout(scope.clientId(), scope.entityNos(), scope.localPartnerIds()));
    }

    /** 截断时间精度后插入并核对影响行数。 */
    @Override
    public void insert(CommercialTermPlan commercialTermPlan) {
        commercialTermPlan.setCreateTime(micros(commercialTermPlan.getCreateTime()));
        commercialTermPlan.setUpdateTime(micros(commercialTermPlan.getUpdateTime()));
        if (mapper.insert(commercialTermPlan) != 1) {
            throw new ErrorException("商业条款方案插入行数不符: " + commercialTermPlan.getTermPlanId());
        }
    }

    /** 按主键读取。 */
    @Override
    public CommercialTermPlan findById(long termPlanId) {
        return mapper.selectById(termPlanId);
    }

    /** 按唯一键（条款类型、方案编号）读取。 */
    @Override
    public CommercialTermPlan findByTypeAndCode(String termType, String planCode) {
        return mapper.selectByTypeAndCode(termType, planCode);
    }

    /** 按唯一键（合同、条款类型）读取。 */
    @Override
    public CommercialTermPlan findByAgreementAndType(long agreementId, String termType) {
        return mapper.selectByAgreementAndType(agreementId, termType);
    }

    /** 按唯一键（Local Partner、条款类型）读取。 */
    @Override
    public CommercialTermPlan findByPartnerAndType(long localPartnerId, String termType) {
        return mapper.selectByPartnerAndType(localPartnerId, termType);
    }

    /** 读取一份合同下的全部方案。 */
    @Override
    public List<CommercialTermPlan> findByAgreementId(long agreementId) {
        return List.copyOf(mapper.selectByAgreementId(agreementId));
    }

    /** 条件迁移，成功后同步原对象。 */
    @Override
    public void updateStatus(CommercialTermPlan commercialTermPlan, CommercialTermPlanStatus status, Instant now) {
        Instant time = micros(now);
        if (mapper.updateStatus(commercialTermPlan, status, time) != 1) {
            throw new ConcurrentRequestException("方案状态已变化: " + commercialTermPlan.getTermPlanId());
        }
        commercialTermPlan.setStatus(status);
        commercialTermPlan.setUpdateTime(time);
    }

    /** 一页方案。 */
    @Override
    public List<CommercialTermPlan> findPage(CommercialTermPlanListQuery query) {
        return List.copyOf(mapper.selectPage(query, query.getPaging().offset(), query.getPaging().size()));
    }

    /** 相同条件的总数。 */
    @Override
    public long count(CommercialTermPlanListQuery query) {
        return mapper.countPage(query);
    }

    /** 空集合不访问数据库。 */
    @Override
    public List<CommercialTermPlan> findByIds(Collection<Long> termPlanIds) {
        if (termPlanIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(mapper.selectByIds(termPlanIds));
    }

    /** 按可选条件读取标准价目模板方案。 */
    @Override
    public List<CommercialTermPlan> findTemplates(String entityNo, String termType, CommercialTermPlanStatus status) {
        return List.copyOf(mapper.selectTemplates(entityNo, termType, status));
    }
}
