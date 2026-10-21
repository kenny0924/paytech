package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 外部接入方持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface IntegrationMapper {

    /** 按不可变编码读取接入方。 */
    Integration byCode(@Param("code") String code);

    /** 在调用方事务中锁定接入方行，供版本校验及条件更新。 */
    Integration lock(@Param("code") String code);

    /** 按标识批量读取接入方，按编码排序。 */
    List<Integration> byIds(@Param("ids") Collection<Long> ids);

    /** 分页查询，按编码与标识稳定排序。 */
    List<Integration> list(@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(@Param("keyword") String keyword);

    /** 插入新接入方，编码由唯一索引保护。 */
    int insert(@Param("row") Integration row);

    /** 按原版本更新名称、说明与出站地址，并递增版本。 */
    int update(@Param("row") Integration row, @Param("integrationName") String integrationName, @Param("description") String description, @Param("endpoints") String endpoints, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除接入方。 */
    int delete(@Param("row") Integration row);
}
