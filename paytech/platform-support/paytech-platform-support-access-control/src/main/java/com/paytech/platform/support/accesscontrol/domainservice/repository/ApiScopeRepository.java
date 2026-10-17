package com.paytech.platform.support.accesscontrol.domainservice.repository;

import java.time.Instant;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;

/** Client API 管理持久化，不向其他模块发布 DataObject。 */
public interface ApiScopeRepository {
    /** 按不可变编码读取 Scope 目录定义。 */
    ApiScope scope(String code);
    /** 在调用方本库事务中锁定目标行，供版本校验及条件更新使用。 */
    ApiScope lock(String code);
    /** 分页查询 Scope 信息，保留服务端排序及总数。 */
    java.util.List<ApiScope> scopes(String keyword, long offset, int size);
    /** 使用与列表相同的筛选条件统计总数。 */
    long count(String keyword);
    /** 插入新 Scope 定义，由唯一索引保护编码不重复。 */
    void insert(ApiScope row);
    /** 按预期版本更新 Scope 名称和说明，编码保持不变。 */
    void update(ApiScope row, String displayName, String description, Instant updateTime);
}
