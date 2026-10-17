package com.paytech.platform.support.integration.domainservice.repository;

import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 标准 API的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface StandardApiRepository {

    /** 按不可变编码读取标准 API。 */
    StandardApi byCode(String code);

    /** 在调用方事务中锁定标准 API 行，供版本校验及条件更新。 */
    StandardApi lock(String code);

    /** 按标识批量读取标准 API，按编码排序。 */
    List<StandardApi> byIds(Collection<Long> ids);

    /** 分页查询，按编码与标识稳定排序。 */
    List<StandardApi> list(String keyword, long offset, int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(String keyword);

    /** 插入新标准 API，编码由唯一索引保护。 */
    void insert(StandardApi row);

    /** 按原版本更新名称与说明，并递增版本。 */
    void update(StandardApi row, String standardApiName, String description, long updatedBy, Instant updateTime);

    /** 按原版本删除标准 API。 */
    void delete(StandardApi row);
}
