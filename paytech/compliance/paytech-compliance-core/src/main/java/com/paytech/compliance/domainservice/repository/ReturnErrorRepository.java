package com.paytech.compliance.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.ReturnError;

/** 退回需求级错误持久化（t_dd_return_error）。 */
public interface ReturnErrorRepository {
    /** 新增一行；影响行数不为 1 时失败。 */
    void insert(ReturnError row);

    /** 主案件的退回错误；空集合返回空列表。 */
    List<ReturnError> findByCase(long caseId);
}
