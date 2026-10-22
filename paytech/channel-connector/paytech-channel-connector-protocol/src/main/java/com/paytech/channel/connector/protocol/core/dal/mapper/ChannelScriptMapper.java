package com.paytech.channel.connector.protocol.core.dal.mapper;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelChildCount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 公共脚本持久化，业务 SQL 位于对应 Mapper XML。 */
@Mapper
public interface ChannelScriptMapper {

    /** 按渠道与不可变编码读取脚本。 */
    ChannelScript byCode(@Param("channelId") long channelId, @Param("code") String code);

    /** 在调用方事务中锁定脚本行，供版本校验及条件更新。 */
    ChannelScript lock(@Param("channelId") long channelId, @Param("code") String code);

    /** 读取渠道全部脚本，按编码排序；每个渠道最多 100 个。 */
    List<ChannelScript> allByChannel(@Param("channelId") long channelId);

    /** 分页查询，可按阶段筛选，按编码与标识稳定排序。 */
    List<ChannelScript> list(@Param("channelId") long channelId, @Param("keyword") String keyword, @Param("executionStage") ScriptExecutionStage executionStage, @Param("offset") long offset, @Param("size") int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(@Param("channelId") long channelId, @Param("keyword") String keyword, @Param("executionStage") ScriptExecutionStage executionStage);

    /** 按渠道统计公共脚本数量，没有脚本的渠道不返回。 */
    List<ChannelChildCount> countByChannels(@Param("channelIds") Collection<Long> channelIds);

    /** 插入新脚本，渠道内编码由唯一索引保护。 */
    int insert(@Param("row") ChannelScript row);

    /** 按原版本更新脚本内容，并递增版本。 */
    int update(@Param("row") ChannelScript row, @Param("scriptName") String scriptName, @Param("executionStage") ScriptExecutionStage executionStage, @Param("parameterDefinition") String parameterDefinition, @Param("sourceCode") String sourceCode, @Param("description") String description, @Param("updatedBy") long updatedBy, @Param("updateTime") Instant updateTime);

    /** 按原版本删除脚本。 */
    int delete(@Param("row") ChannelScript row);
}
