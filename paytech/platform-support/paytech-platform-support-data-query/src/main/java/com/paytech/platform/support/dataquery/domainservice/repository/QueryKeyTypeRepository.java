package com.paytech.platform.support.dataquery.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryKeyType;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;

/** t_query_key_type 仓储。 */
public interface QueryKeyTypeRepository {

    /**
     * 插入新键类型。
     *
     * @param keyType 已准备主键、版本与时间的新对象
     * @throws com.paytech.foundation.common.error.BizCheckFailException 编码已存在
     */
    void insert(QueryKeyType keyType);

    /** 按主键查询；不存在返回 null。 */
    QueryKeyType findById(long keyTypeId);

    /** 在调用方事务内加排他锁读取，与模板保存时的共享锁互斥；不存在返回 null。 */
    QueryKeyType findByIdForUpdate(long keyTypeId);

    /** 按编码批量查询；空集合返回空列表。 */
    List<QueryKeyType> findByCodes(Collection<String> codes);

    /** 在调用方事务内按编码加共享锁读取，阻止并发停用与修改值类型；空集合返回空列表。 */
    List<QueryKeyType> findByCodesForShare(Collection<String> codes);

    /** 全部键类型，按 sort_no、编码升序。 */
    List<QueryKeyType> findAll();

    /** 在调用方事务内对全部键类型加排他锁，按主键升序，供整体重排。 */
    List<QueryKeyType> findAllForUpdate();

    /** 分页查询；筛选为 null 表示不限。 */
    List<QueryKeyType> findPage(QueryKeyTypeStatus status, String keyword, PagingRequestBody paging);

    /** 与分页相同条件的总数。 */
    long count(QueryKeyTypeStatus status, String keyword);

    /**
     * 按原版本更新名称、分组、值类型、默认角色、入口设置、顺序号与说明，成功后同步原对象。
     *
     * @param keyType 当前对象
     * @param next 目标内容与修改人、时间
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 版本已变化
     */
    void updateDefinition(QueryKeyType keyType, QueryKeyType next);

    /** 按原版本与原状态切换状态，成功后同步原对象。 */
    void changeStatus(QueryKeyType keyType, QueryKeyTypeStatus status, long updatedBy, Instant updateTime);

    /** 按原版本改写顺序号，成功后同步原对象。 */
    void changeSortNo(QueryKeyType keyType, int sortNo, long updatedBy, Instant updateTime);
}
