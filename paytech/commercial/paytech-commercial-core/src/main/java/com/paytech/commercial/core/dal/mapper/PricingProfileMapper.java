package com.paytech.commercial.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.domain.enums.PricingProfileStatus;

/** t_pricing_profile 的持久化查询。 */
public interface PricingProfileMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 pricing_profile 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.PricingProfile> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 插入一行完整记录，返回影响行数。 */
    int insert(PricingProfile pricingProfile);

    /** 按主键读取，不存在返回 null。 */
    PricingProfile selectById(@Param("pricingProfileId") long pricingProfileId);

    /** 按画像代码（uk_pricing_profile_code）读取，不存在返回 null。 */
    PricingProfile selectByCode(@Param("pricingProfileCode") String pricingProfileCode);

    /** 按一组画像代码读取，按 pricing_profile_id 升序；pricingProfileCodes 必须非空。 */
    List<PricingProfile> selectByCodes(@Param("pricingProfileCodes") Collection<String> pricingProfileCodes);

    /** 按种类与状态筛选画像，参数为 null 表示不限，按主键升序。 */
    List<PricingProfile> selectByKindAndStatus(@Param("profileKind") PricingProfileKind profileKind,
        @Param("status") PricingProfileStatus status);
    /** 仅当状态仍为 row.status 时迁移状态，返回影响行数。 */
    int updateStatus(@Param("row") PricingProfile row, @Param("status") PricingProfileStatus status, @Param("now") Instant now);

    /** 仅当状态与 update_time 均未变化时改写名称、种类与 Payment Method，返回影响行数。 */
    int updateDraft(@Param("row") PricingProfile row, @Param("profileName") String profileName,
        @Param("profileKind") PricingProfileKind profileKind, @Param("paymentMethodCode") String paymentMethodCode,
        @Param("now") Instant now);
}
