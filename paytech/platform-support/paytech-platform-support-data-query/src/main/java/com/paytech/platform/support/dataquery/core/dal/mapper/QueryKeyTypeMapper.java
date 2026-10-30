package com.paytech.platform.support.dataquery.core.dal.mapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.dataquery.core.dal.dataobject.QueryKeyType;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;

/** t_query_key_type 的 MyBatis 映射；SQL 在 mapper/dataquery/QueryKeyTypeMapper.xml。 */
public interface QueryKeyTypeMapper {

    /** 插入新键类型。 */
    int insert(@Param("row") QueryKeyType row);

    /** 按主键查询。 */
    QueryKeyType selectById(@Param("keyTypeId") long keyTypeId);

    /** 按主键加排他锁读取。 */
    QueryKeyType selectByIdForUpdate(@Param("keyTypeId") long keyTypeId);

    /** 按编码批量查询。 */
    List<QueryKeyType> selectByCodes(@Param("codes") Collection<String> codes);

    /** 按编码批量加共享锁读取。 */
    List<QueryKeyType> selectByCodesForShare(@Param("codes") Collection<String> codes);

    /** 全部键类型，按 sort_no、编码升序。 */
    List<QueryKeyType> selectAll();

    /** 全部键类型加排他锁读取，按主键升序。 */
    List<QueryKeyType> selectAllForUpdate();

    /** 分页查询，按 sort_no、编码升序。 */
    List<QueryKeyType> selectPage(@Param("status") QueryKeyTypeStatus status, @Param("keyword") String keyword,
        @Param("offset") int offset, @Param("size") int size);

    /** 与分页相同条件的总数。 */
    long count(@Param("status") QueryKeyTypeStatus status, @Param("keyword") String keyword);

    /** 按原版本更新可修改字段，版本加一。 */
    int updateDefinition(@Param("row") QueryKeyType row, @Param("next") QueryKeyType next);

    /** 按原版本与原状态更新状态，版本加一。 */
    int updateStatus(@Param("row") QueryKeyType row, @Param("status") QueryKeyTypeStatus status,
        @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本改写顺序号，版本加一。 */
    int updateSortNo(@Param("row") QueryKeyType row, @Param("sortNo") int sortNo, @Param("updatedBy") long updatedBy,
        @Param("updateTime") Instant updateTime);
}
