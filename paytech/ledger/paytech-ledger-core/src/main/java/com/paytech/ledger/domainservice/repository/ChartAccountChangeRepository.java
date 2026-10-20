package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.domain.view.ChartAccountChangeView;

/** t_chart_account_change：科目变更记录只追加，按时间倒序读取。 */
public interface ChartAccountChangeRepository {

    /** 追加一条变更记录。 */
    void insert(ChartAccountChange change);

    /** 分页读取科目表的变更记录；chartAccountId 为空时不限定科目，按变更时间倒序。 */
    List<ChartAccountChangeView> findPage(long chartId, Long chartAccountId, PagingRequestBody paging);

    /** 统计符合条件的变更记录数。 */
    long countPage(long chartId, Long chartAccountId);
}
