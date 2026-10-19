package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;

/** t_ChartAccount：会计科目读取。 */
public interface ChartAccountRepository {

    /** 按主键读取科目，不存在返回空。 */
    ChartAccount findById(long id);

    /** 按主键升序读取一组科目；空集合返回空列表。 */
    List<ChartAccount> findByIds(List<Long> ids);

    /** 按科目表和科目代码读取，不存在返回空。 */
    ChartAccount findByChartAndCode(long chartId, String accountCode);

    /** 读取科目表下的科目；postingAllowed 为空时不过滤，按科目代码升序。 */
    List<ChartAccount> findByChart(long chartId, Boolean postingAllowed);
}
