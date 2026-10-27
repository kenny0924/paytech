package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.PartnerOnboardingCode;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_partner_onboarding_code SQL。 */
public interface PartnerOnboardingCodeMapper {
    /** 签发。 */
    int insert(@Param("row") PartnerOnboardingCode row);

    /** 按码读取。 */
    PartnerOnboardingCode findByCode(@Param("code") String code);

    /** 按 ID 读取。 */
    PartnerOnboardingCode findById(@Param("codeId") long codeId);

    /** Partner 的全部入驻码，新签发在前。 */
    List<PartnerOnboardingCode> findByPartner(@Param("localPartnerId") long localPartnerId);

    /** 条件占用一次。 */
    int consume(@Param("codeId") long codeId, @Param("now") Instant now);

    /** ACTIVE 条件作废。 */
    int revoke(@Param("codeId") long codeId, @Param("revokedBy") long revokedBy, @Param("now") Instant now);
}
