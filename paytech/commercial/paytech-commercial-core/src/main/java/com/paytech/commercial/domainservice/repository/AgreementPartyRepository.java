package com.paytech.commercial.domainservice.repository;

import java.util.List;

import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.AgreementParty;

/** t_agreement_party 的本地仓储；不负责其他表的加载或事务。 */
public interface AgreementPartyRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_party 记录，保留历史和未生效事实供业务判断。 */
    List<AgreementParty> findForCheckout(CommercialCheckoutScope scope);

    /** 被任一合同参与方引用的 Party ID；空集合不访问数据库。 */
    java.util.Set<Long> findReferencedPartyIds(java.util.Collection<Long> partyIds);

    /** 被任一合同参与方引用的资料 ID；空集合不访问数据库。 */
    java.util.Set<Long> findReferencedPartyProfileIds(java.util.Collection<Long> partyProfileIds);
}
