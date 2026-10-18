package com.paytech.foundation.messaging.auto.sequenced;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 发布侧串行测试读取补偿任务状态；只读与清理。
 */
@Mapper
public interface SequencedCompensationMapper {

    @Select("SELECT execute_status FROM t_compensation_event WHERE id = #{id}")
    String findStatus(@Param("id") long id);

    @Select("SELECT last_error_code FROM t_compensation_event WHERE id = #{id}")
    String findErrorCode(@Param("id") long id);

    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE id = #{id}")
    int count(@Param("id") long id);

    @Delete("DELETE FROM t_compensation_event")
    int deleteAll();
}
