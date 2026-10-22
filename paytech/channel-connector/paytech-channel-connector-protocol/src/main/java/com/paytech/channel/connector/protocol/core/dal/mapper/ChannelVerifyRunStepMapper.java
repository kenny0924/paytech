package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRunStep;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 验证运行步骤持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ChannelVerifyRunStepMapper {

    /** 读取运行的全部步骤，按序号排序。 */
    List<ChannelVerifyRunStep> byRun(@Param("verifyRunId") long verifyRunId);

    /** 一次插入运行的全部步骤。 */
    int insertAll(@Param("rows") List<ChannelVerifyRunStep> rows);
}
