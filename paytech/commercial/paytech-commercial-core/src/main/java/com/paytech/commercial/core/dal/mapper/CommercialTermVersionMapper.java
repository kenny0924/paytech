package com.paytech.commercial.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.CommercialTermVersion;
import com.paytech.commercial.domain.enums.CommercialTermVersionStatus;

/** t_commercial_term_version 的持久化查询。 */
public interface CommercialTermVersionMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 commercial_term_version 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.CommercialTermVersion> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 插入一行完整记录，返回影响行数。 */
    int insert(CommercialTermVersion commercialTermVersion);

    /** 按主键读取，不存在返回 null。 */
    CommercialTermVersion selectById(@Param("termVersionId") long termVersionId);

    /** 按唯一键（方案、版本号）读取，不存在返回 null。 */
    CommercialTermVersion selectByPlanAndVersionNo(@Param("termPlanId") long termPlanId, @Param("versionNo") long versionNo);

    /** 读取一个方案的全部版本，按版本号升序，按 version_no 升序。 */
    List<CommercialTermVersion> selectByPlanId(@Param("termPlanId") long termPlanId);

    /** 方案内当前最大版本号，没有版本时返回 null。 */
    Long selectMaxVersionNo(@Param("termPlanId") long termPlanId);

    /** 按商业时点选取方案下唯一适用版本，没有时返回 null。 */
    CommercialTermVersion findEffectiveAt(@Param("termPlanId") long termPlanId, @Param("asOf") Instant asOf);

    /** 仅当状态仍为 row.status 时写入状态与 active_slot，返回影响行数。 */
    int updateStatus(@Param("row") CommercialTermVersion row, @Param("status") CommercialTermVersionStatus status,
        @Param("activeSlot") Integer activeSlot, @Param("now") Instant now);
    /** 仅当状态与 update_time 均未变化时改写生效区间，返回影响行数。 */
    int updateEffectivePeriod(@Param("row") CommercialTermVersion row, @Param("effectiveFrom") Instant effectiveFrom,
        @Param("effectiveTo") Instant effectiveTo, @Param("now") Instant now);
}
