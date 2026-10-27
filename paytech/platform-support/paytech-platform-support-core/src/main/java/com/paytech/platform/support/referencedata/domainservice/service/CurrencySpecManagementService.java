package com.paytech.platform.support.referencedata.domainservice.service;

import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;
import com.paytech.platform.support.referencedata.domain.ChangeCurrencySpecStatusContext;
import com.paytech.platform.support.referencedata.domain.UpdateCurrencySpecContext;

/** 币种本表保存；加入调用方 Processor 的本地事务，不访问外部能力。 */
public interface CurrencySpecManagementService {

    /** 插入已准备好的 DRAFT 币种。 */
    void create(ReferenceCurrency currency);

    /** 按原版本与原状态保存新属性。 */
    void update(UpdateCurrencySpecContext context);

    /** 按原版本与原状态保存新状态。 */
    void changeStatus(ChangeCurrencySpecStatusContext context);
}
