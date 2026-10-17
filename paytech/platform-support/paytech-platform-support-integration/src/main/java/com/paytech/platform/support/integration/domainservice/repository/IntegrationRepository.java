package com.paytech.platform.support.integration.domainservice.repository;

import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 外部接入方的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface IntegrationRepository {

    /** 按不可变编码读取接入方。 */
    Integration byCode(String code);

    /** 在调用方事务中锁定接入方行，供版本校验及条件更新。 */
    Integration lock(String code);

    /** 按标识批量读取接入方，按编码排序。 */
    List<Integration> byIds(Collection<Long> ids);

    /** 分页查询，按编码与标识稳定排序。 */
    List<Integration> list(String keyword, long offset, int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(String keyword);

    /** 插入新接入方，编码由唯一索引保护。 */
    void insert(Integration row);

    /** 按原版本更新名称、说明与出站地址，并递增版本。 */
    void update(Integration row, String integrationName, String description, String endpoints, long updatedBy, Instant updateTime);

    /** 按原版本删除接入方。 */
    void delete(Integration row);
}
