package com.paytech.platform.support.referencedata.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CountrySubdivision;

/** 行政区单表仓储。 */
public interface CountrySubdivisionRepository {

    /** 按可选国家、状态与行政区代码查询，按行政区代码升序；条件为空不限制。 */
    List<CountrySubdivision> find(String countryCode, ReferenceDataStatus status, List<String> codes);
}
