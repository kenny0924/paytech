package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 标准 API持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface StandardApiMapper {

    /** 按不可变编码读取标准 API。 */
    StandardApi byCode(@Param("code") String code);

    /** 在调用方事务中锁定标准 API 行，供版本校验及条件更新。 */
    StandardApi lock(@Param("code") String code);

    /** 按标识批量读取标准 API，按编码排序。 */
    List<StandardApi> byIds(@Param("ids") Collection<Long> ids);

    /** 分页查询，按编码与标识稳定排序。 */
    List<StandardApi> list(@Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(@Param("keyword") String keyword);

    /** 插入新标准 API，编码由唯一索引保护。 */
    int insert(@Param("row") StandardApi row);

    /** 按原版本更新名称与说明，并递增版本。 */
    int update(@Param("row") StandardApi row, @Param("standardApiName") String standardApiName, @Param("description") String description, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除标准 API。 */
    int delete(@Param("row") StandardApi row);
}
