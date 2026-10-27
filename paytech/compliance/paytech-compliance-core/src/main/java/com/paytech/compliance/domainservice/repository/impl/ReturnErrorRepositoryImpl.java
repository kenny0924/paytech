package com.paytech.compliance.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.compliance.core.dal.dataobject.ReturnError;
import com.paytech.compliance.core.dal.mapper.ReturnErrorMapper;
import com.paytech.compliance.domainservice.repository.ReturnErrorRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;

/** 退回需求级错误持久化。 */
@Repository
@AllArgsConstructor
public class ReturnErrorRepositoryImpl implements ReturnErrorRepository {
    private final ReturnErrorMapper mapper;

    /** 新增一行。 */
    @Override
    public void insert(ReturnError row) {
        if (mapper.insert(row) != 1) {
            throw new ErrorException("t_dd_return_error 写入行数异常");
        }
    }

    /** 主案件的退回错误。 */
    @Override
    public List<ReturnError> findByCase(long caseId) {
        return mapper.findByCase(caseId);
    }
}
