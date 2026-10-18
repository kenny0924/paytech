package com.paytech.platform.support.integration.domainservice.repository;

import com.paytech.platform.support.integration.core.dal.dataobject.ApiField;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import java.util.Collection;
import java.util.List;

/** API 字段的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface ApiFieldRepository {

    /** 按 API 与方向批量读取字段，按顺序号排序。 */
    List<ApiField> byApis(Collection<Long> apiIds, Collection<ApiFieldDirection> directions);

    /** 删除某个 API 指定方向的全部字段，返回删除行数。 */
    int deleteByApi(long apiId, Collection<ApiFieldDirection> directions);

    /** 批量插入字段，行数必须与输入一致。 */
    void insertAll(List<ApiField> rows);
}
