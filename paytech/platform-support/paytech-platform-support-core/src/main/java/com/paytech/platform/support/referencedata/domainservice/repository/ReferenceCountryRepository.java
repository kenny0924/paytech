package com.paytech.platform.support.referencedata.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCountry;

/** 国家单表仓储。 */
public interface ReferenceCountryRepository {

    /** 按可选状态与国家代码查询，按国家代码升序；条件为空不限制。 */
    List<ReferenceCountry> find(ReferenceDataStatus status, List<String> codes);
}
