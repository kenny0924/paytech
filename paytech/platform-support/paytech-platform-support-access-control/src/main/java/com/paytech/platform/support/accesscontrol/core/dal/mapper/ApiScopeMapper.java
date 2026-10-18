package com.paytech.platform.support.accesscontrol.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.accesscontrol.core.dal.dataobject.ApiScope;

/** 业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ApiScopeMapper {
    /** 按不可变编码读取 Scope 目录定义。 */
    ApiScope scope(@Param("code") String code);
    /** 在调用方本库事务中锁定目标行，供版本校验及条件更新使用。 */
    ApiScope lock(@Param("code") String code);
    /** 分页查询 Scope 信息，保留服务端排序及总数。 */
    java.util.List<ApiScope> scopes(@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);
    /** 使用与列表相同的筛选条件统计总数。 */
    long count(@Param("keyword") String keyword);
    /** 插入新 Scope 定义，由唯一索引保护编码不重复。 */
    int insert(@Param("row") ApiScope row);
    /** 按预期版本更新 Scope 名称和说明，编码保持不变。 */
    int update(@Param("row") ApiScope row, @Param("displayName") String displayName,
        @Param("description") String description, @Param("updateTime") Instant updateTime);
}
