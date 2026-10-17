package com.paytech.billing.domainservice.repository;

import com.paytech.billing.core.dal.dataobject.EconomicFact;

/** t_economic_fact：经济事实读取。 */
public interface EconomicFactRepository {

    /** 按主键读取，不存在返回空。 */
    EconomicFact findById(long id);
}
