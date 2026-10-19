package com.paytech.commercial.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.AgreementParty;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords;
import com.paytech.commercial.domain.enums.AgreementPartyPartyRole;

/** t_agreement_party 的持久化查询。 */
public interface AgreementPartyMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_party 记录，保留历史和未生效事实供业务判断。 */
    List<CheckoutRecords.AgreementParty> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 被任一合同参与方引用的 Party ID；partyIds 必须非空。 */
    List<Long> findReferencedPartyIds(@Param("partyIds") java.util.Collection<Long> partyIds);

    /** 被任一合同参与方引用的资料 ID；partyProfileIds 必须非空。 */
    List<Long> findReferencedPartyProfileIds(@Param("partyProfileIds") java.util.Collection<Long> partyProfileIds);

    /** 批量插入完整记录，返回影响行数；rows 必须非空。 */
    int insertAll(@Param("rows") List<AgreementParty> rows);

    /** 按主键读取，不存在返回 null。 */
    AgreementParty selectById(@Param("agreementPartyId") long agreementPartyId);

    /** 按唯一键（合同、Party、角色）读取，不存在返回 null。 */
    AgreementParty selectByUniqueKey(@Param("agreementId") long agreementId, @Param("partyId") long partyId, @Param("partyRole") AgreementPartyPartyRole partyRole);

    /** 读取一份合同的全部 Party 角色，按 agreement_party_id 升序。 */
    List<AgreementParty> selectByAgreementId(@Param("agreementId") long agreementId);

    /** 按合同接受事件编号读取已登记的角色行，用于同引用重放判断，按 agreement_party_id 升序。 */
    List<AgreementParty> selectByAcceptanceReference(@Param("acceptanceReference") String acceptanceReference);

    /** 仅当该角色行尚未登记签署（accepted_time 为空）时写入 acceptance 的八个签署字段，返回影响行数。 */
    int updateAcceptance(@Param("row") AgreementParty row, @Param("acceptance") AgreementParty acceptance,
        @Param("now") Instant now);
}
