package com.paytech.commercial.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.api.enums.CommercialTermPlanStatus;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermPlan;

/** t_commercial_term_plan 的持久化查询。 */
public interface CommercialTermPlanMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_plan 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermPlan> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 插入一行完整记录，返回影响行数。 */
    int insert(CommercialTermPlan commercialTermPlan);

    /** 按主键读取，不存在返回 null。 */
    CommercialTermPlan selectById(@Param("termPlanId") long termPlanId);

    /** 按唯一键（条款类型、方案编号）读取，不存在返回 null。 */
    CommercialTermPlan selectByTypeAndCode(@Param("termType") String termType, @Param("planCode") String planCode);

    /** 按唯一键（合同、条款类型）读取，不存在返回 null。 */
    CommercialTermPlan selectByAgreementAndType(@Param("agreementId") long agreementId, @Param("termType") String termType);

    /** 按唯一键（Local Partner、条款类型）读取，不存在返回 null。 */
    CommercialTermPlan selectByPartnerAndType(@Param("localPartnerId") long localPartnerId, @Param("termType") String termType);

    /** 读取一份合同下的全部方案，按 term_plan_id 升序。 */
    List<CommercialTermPlan> selectByAgreementId(@Param("agreementId") long agreementId);

    /** 仅当状态仍为 row.status 时迁移状态，返回影响行数。 */
    int updateStatus(@Param("row") CommercialTermPlan row, @Param("status") CommercialTermPlanStatus status, @Param("now") Instant now);
}
