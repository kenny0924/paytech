package com.paytech.ledger.domainservice.repository;

import com.paytech.ledger.core.dal.dataobject.ChartAccount;

/** t_ChartAccount：会计科目读取。 */
public interface ChartAccountRepository {

    /** 按主键读取科目，不存在返回空。 */
    ChartAccount findById(long id);

    /** 按科目表和科目代码读取，不存在返回空。 */
    ChartAccount findByChartAndCode(long chartId, String accountCode);
}
