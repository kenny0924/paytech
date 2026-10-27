package com.paytech.platform.support.referencedata.domainservice.repository;

import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.enums.ReferenceDataStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.Mcc;

/** MCC 单表仓储。 */
public interface MccRepository {

    /**
     * 按条件分页查询，按 MCC 编码升序
     *
     * @param keyword 关键字，匹配编码前缀或英文名包含（不区分大小写）；为空不限制
     * @param status 状态；为空不限制
     * @param codes MCC 编码；为空不限制
     * @param paging 已校验的分页
     * @return 一页与同条件总数
     */
    PagingResponseBody<Mcc> search(String keyword, ReferenceDataStatus status, List<String> codes,
        PagingRequestBody paging);
}
