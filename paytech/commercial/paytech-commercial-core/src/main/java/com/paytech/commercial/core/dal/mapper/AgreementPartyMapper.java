package com.paytech.commercial.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.AgreementParty;

/** t_agreement_party 的持久化查询。 */
public interface AgreementPartyMapper {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_party 记录，保留历史和未生效事实供业务判断。 */
    List<AgreementParty> findForCheckout(@Param("clientId") long clientId, @Param("entityNos") List<String> entityNos,
        @Param("localPartnerIds") List<Long> localPartnerIds);

    /** 被任一合同参与方引用的 Party ID；partyIds 必须非空。 */
    List<Long> findReferencedPartyIds(@Param("partyIds") java.util.Collection<Long> partyIds);

    /** 被任一合同参与方引用的资料 ID；partyProfileIds 必须非空。 */
    List<Long> findReferencedPartyProfileIds(@Param("partyProfileIds") java.util.Collection<Long> partyProfileIds);
}
