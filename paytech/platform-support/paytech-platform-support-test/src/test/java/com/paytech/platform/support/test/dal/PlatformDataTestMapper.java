package com.paytech.platform.support.test.dal;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 测试专属表的计数与整表回收；表名只来自 PlatformDataCleaner 的固定白名单。 */
public interface PlatformDataTestMapper {

    /** 统计白名单表的行数。 */
    @Select("SELECT COUNT(*) FROM ${table}")
    long count(@Param("table") String table);

    /** 删除白名单表的全部测试数据。 */
    @Delete("DELETE FROM ${table}")
    int deleteAll(@Param("table") String table);

    /** 读取当前库名，防止误连非本地业务库。 */
    @Select("SELECT DATABASE()")
    String databaseName();
}
