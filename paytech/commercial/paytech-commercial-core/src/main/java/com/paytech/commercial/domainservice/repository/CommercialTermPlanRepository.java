package com.paytech.commercial.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;
import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.domain.CommercialTermPlanListQuery;

/** t_commercial_term_plan 的本地仓储；不负责其他表的加载或事务。 */
public interface CommercialTermPlanRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_plan 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermPlan> findForCheckout(CommercialCheckoutScope scope);

    /**
     * 插入商业条款方案；影响行数不为 1 时抛出系统异常，唯一键冲突原样抛出。
     * <li>创建与更新时间截断到微秒后写回对象。</li>
     *
     * @param commercialTermPlan 已准备 ID 与全部字段的新行
     */
    void insert(CommercialTermPlan commercialTermPlan);

    /**
     * 按主键读取。
     *
     * @param termPlanId 方案 ID
     * @return 商业条款方案，不存在为 null
     */
    CommercialTermPlan findById(long termPlanId);

    /**
     * 按唯一键（条款类型、方案编号）读取。
     *
     * @param termType 条款类型
     * @param planCode 方案编号
     * @return 商业条款方案，不存在为 null
     */
    CommercialTermPlan findByTypeAndCode(String termType, String planCode);

    /**
     * 按唯一键（合同、条款类型）读取。
     *
     * @param agreementId 合同 ID
     * @param termType 条款类型
     * @return 商业条款方案，不存在为 null
     */
    CommercialTermPlan findByAgreementAndType(long agreementId, String termType);

    /**
     * 按唯一键（Local Partner、条款类型）读取。
     *
     * @param localPartnerId Local Partner ID
     * @param termType 条款类型
     * @return 商业条款方案，不存在为 null
     */
    CommercialTermPlan findByPartnerAndType(long localPartnerId, String termType);

    /**
     * 读取一份合同下的全部方案。
     *
     * @param agreementId 合同 ID
     * @return 按 term_plan_id 升序的商业条款方案
     */
    List<CommercialTermPlan> findByAgreementId(long agreementId);

    /**
     * 以对象当前状态为条件迁移状态；未命中抛出并发冲突，成功后同步原对象的状态与更新时间。
     *
     * @param commercialTermPlan 已读取的行
     * @param status 目标状态
     * @param now 本次更新时间
     */
    void updateStatus(CommercialTermPlan commercialTermPlan, CommercialTermPlanStatus status, Instant now);

    /**
     * 按列表条件读取一页方案，按创建时间、方案 ID 倒序。
     *
     * @param query 已校验的列表条件与分页
     * @return 当前页方案
     */
    List<CommercialTermPlan> findPage(CommercialTermPlanListQuery query);

    /**
     * 相同列表条件下的方案总数。
     *
     * @param query 列表条件
     * @return 总数
     */
    long count(CommercialTermPlanListQuery query);

    /**
     * 按一组主键读取方案；空集合不访问数据库。
     *
     * @param termPlanIds 方案 ID
     * @return 按 term_plan_id 升序
     */
    List<CommercialTermPlan> findByIds(Collection<Long> termPlanIds);
}
