package com.paytech.channel.connector.protocol.domainservice.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.domain.ChannelSummary;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** t_channel 单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface ChannelRepository {

    /** 按主键读取，未找到返回 null。 */
    Channel byId(long channelId);

    /** 按不可变编码读取，未找到返回 null。 */
    Channel byCode(String code);

    /** 在调用方事务中按编码锁定渠道行，供下级配置写入与版本校验。 */
    Channel lock(String code);

    /** 在调用方事务中按主键锁定渠道行。 */
    Channel lockById(long channelId);

    /** 按主键批量读取，按编码排序；空集合返回空列表。 */
    List<Channel> byIds(Collection<Long> ids);

    /** 插入新渠道；编码重复按并发冲突拒绝。 */
    void insert(Channel row);

    /** 未关闭渠道修改名称与说明并递增版本；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateProfile(Channel row, String channelName, String description, long updatedBy, Instant now);

    /** 按原对象版本整组替换出站地址并递增版本；版本不一致或已关闭按并发冲突拒绝，成功后同步原对象。 */
    void updateEndpoints(Channel row, String endpoints, long updatedBy, Instant now);

    /**
     * 以期望原状态为条件变更状态
     * <li>渠道状态由支付扩展（core）经渠道服务变更，调用方持有的是领域结果，SQL 成功后由渠道服务同步该结果。</li>
     * <li>行数不为 1 按并发冲突拒绝。</li>
     */
    void updateStatus(long channelId, ChannelStatus fromStatus, ChannelStatus toStatus, long updatedBy, Instant now);

    /** 按类型、状态与编码 / 名称前缀分页读取列表项；数据库 LIMIT/OFFSET 与 COUNT 使用相同条件。 */
    PagingResponseBody<ChannelSummary> findSummaries(ChannelType channelType, ChannelStatus status, String keyword,
        PagingRequestBody paging);
}
