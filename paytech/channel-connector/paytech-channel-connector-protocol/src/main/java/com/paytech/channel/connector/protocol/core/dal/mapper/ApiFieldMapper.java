package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ApiField;
import com.paytech.channel.connector.protocol.core.enums.ApiFieldDirection;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** API 字段持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ApiFieldMapper {

    /** 按 API 与方向批量读取字段，按顺序号排序。 */
    List<ApiField> byApis(@Param("apiIds") Collection<Long> apiIds, @Param("directions") Collection<ApiFieldDirection> directions);

    /** 删除某个 API 指定方向的全部字段，返回删除行数。 */
    int deleteByApi(@Param("apiId") long apiId, @Param("directions") Collection<ApiFieldDirection> directions);

    /** 批量插入字段，行数必须与输入一致。 */
    int insertAll(@Param("rows") List<ApiField> rows);
}
