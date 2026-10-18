package com.paytech.commercial.domainservice.repository;

import java.util.List;

import com.paytech.commercial.domain.CommercialCheckoutScope;
import com.paytech.commercial.core.dal.dataobject.CheckoutRecords.AgreementOperatingEntity;

/** t_agreement_operating_entity 的本地仓储；不负责其他表的加载或事务。 */
public interface AgreementOperatingEntityRepository {
    /** 读取本 Client Checkout 范围（Client 及 client-master 返回的 OE 与 Local Partner）内的 agreement_operating_entity 记录，保留历史和未生效事实供业务判断。 */
    List<AgreementOperatingEntity> findForCheckout(CommercialCheckoutScope scope);
}
