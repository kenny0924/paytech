package com.paytech.platform.support.referencedata.domainservice.service;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.domain.ChangeCurrencySpecStatusContext;
import com.paytech.platform.support.referencedata.domain.UpdateCurrencySpecContext;

/** 币种规则本表保存；加入调用方 Processor 的本地事务，不访问外部能力。 */
public interface CurrencySpecManagementService {

    /** 插入已准备好的 DRAFT 规则。 */
    void create(CurrencySpec spec);

    /** 按原版本保存 DRAFT 规则的新小数位与舍入方式。 */
    void update(UpdateCurrencySpecContext context);

    /** 按原版本与原状态保存新状态。 */
    void changeStatus(ChangeCurrencySpecStatusContext context);
}
