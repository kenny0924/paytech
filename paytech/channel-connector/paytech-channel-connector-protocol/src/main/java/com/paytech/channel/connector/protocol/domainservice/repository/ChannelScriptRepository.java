package com.paytech.channel.connector.protocol.domainservice.repository;

import java.util.Collection;
import java.util.Map;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelScript;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import java.time.Instant;
import java.util.List;

/** 公共脚本的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface ChannelScriptRepository {

    /** 按渠道与不可变编码读取脚本。 */
    ChannelScript byCode(long channelId, String code);

    /** 在调用方事务中锁定脚本行，供版本校验及条件更新。 */
    ChannelScript lock(long channelId, String code);

    /** 读取渠道全部脚本，按编码排序；每个渠道最多 100 个。 */
    List<ChannelScript> allByChannel(long channelId);

    /** 分页查询，可按阶段筛选，按编码与标识稳定排序。 */
    List<ChannelScript> list(long channelId, String keyword, ScriptExecutionStage executionStage, long offset, int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(long channelId, String keyword, ScriptExecutionStage executionStage);

    /** 按渠道统计公共脚本数量；空集合返回空映射。 */
    Map<Long, Long> countByChannels(Collection<Long> channelIds);

    /** 插入新脚本，渠道内编码由唯一索引保护。 */
    void insert(ChannelScript row);

    /** 按原版本更新脚本内容，并递增版本。 */
    void update(ChannelScript row, String scriptName, ScriptExecutionStage executionStage, String parameterDefinition, String sourceCode, String description, long updatedBy, Instant updateTime);

    /** 按原版本删除脚本。 */
    void delete(ChannelScript row);
}
