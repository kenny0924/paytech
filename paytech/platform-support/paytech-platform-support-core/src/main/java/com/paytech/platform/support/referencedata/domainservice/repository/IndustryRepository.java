package com.paytech.platform.support.referencedata.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Industry;

/** 行业单表仓储。 */
public interface IndustryRepository {

    /** 按可选层级、上级大类、状态与行业编码查询；每个大类之后紧跟其细类，条件为空不限制。 */
    List<Industry> find(Integer level, String parentCode, ReferenceDataStatus status, List<String> codes);
}
