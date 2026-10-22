package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRun;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 验证运行持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ChannelVerifyRunMapper {

    /** 按内部标识读取运行。 */
    ChannelVerifyRun byId(@Param("verifyRunId") long verifyRunId);

    /** 分页查询渠道的运行，可按用例筛选，按开始时间倒序。 */
    List<ChannelVerifyRun> list(@Param("channelId") long channelId, @Param("verifyCaseId") Long verifyCaseId, @Param("offset") long offset, @Param("size") int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(@Param("channelId") long channelId, @Param("verifyCaseId") Long verifyCaseId);

    /** 每个用例最近一次运行（任意状态）。 */
    List<ChannelVerifyRun> latestByCases(@Param("verifyCaseIds") Collection<Long> verifyCaseIds);

    /** 每个用例最近一次已得出结论的运行。 */
    List<ChannelVerifyRun> latestFinishedByCases(@Param("verifyCaseIds") Collection<Long> verifyCaseIds);

    /** 插入运行中的记录。 */
    int insert(@Param("row") ChannelVerifyRun row);

    /** 只在仍为 RUNNING 时写入结论与证据。 */
    int finish(@Param("row") ChannelVerifyRun row, @Param("runStatus") VerifyRunStatus runStatus, @Param("configVersionsJson") String configVersionsJson, @Param("endpointsJson") String endpointsJson, @Param("failureSummary") String failureSummary, @Param("finishTime") Instant finishTime);
}
