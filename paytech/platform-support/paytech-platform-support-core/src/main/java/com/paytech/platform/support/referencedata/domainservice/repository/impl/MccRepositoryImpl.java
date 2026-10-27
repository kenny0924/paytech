package com.paytech.platform.support.referencedata.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Mcc;
import com.paytech.platform.support.referencedata.core.dal.mapper.MccMapper;
import com.paytech.platform.support.referencedata.domainservice.repository.MccRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的 MCC 仓储；同一条件执行 LIMIT/OFFSET 与 COUNT。 */
@Repository
@AllArgsConstructor
public class MccRepositoryImpl implements MccRepository {

    private final MccMapper mapper;

    /** 关键字按 ! 转义 LIKE 通配符后作为编码前缀与名称包含条件。 */
    @Override
    public PagingResponseBody<Mcc> search(String keyword, ReferenceDataStatus status, List<String> codes,
        PagingRequestBody paging) {
        String pattern = keyword == null ? null : likeLiteral(keyword);
        long total = mapper.count(pattern, status, codes);
        List<Mcc> items = total == 0 ? List.of()
            : mapper.search(pattern, status, codes, paging.offset(), paging.size());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }

    /** LIKE 字面量：转义 !、%、_。 */
    static String likeLiteral(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
