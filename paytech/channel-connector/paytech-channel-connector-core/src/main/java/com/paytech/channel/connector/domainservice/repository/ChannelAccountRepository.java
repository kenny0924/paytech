package com.paytech.channel.connector.domainservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Collection;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** ChannelAccount 单表只读仓储。 */
public interface ChannelAccountRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<ChannelAccount> findByEntity(String entityNo);

    /** 按内部主键读取，未找到返回空。 */
    ChannelAccount findById(long id);

    // ---- CC-B 追加：技术账号管理写入与分页 ----

    /** 写入新账号。 */
    void insert(ChannelAccount account);

    /** 未关闭账号修改名称与备注；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateProfile(ChannelAccount account, String accountName, String remark, Instant now);

    /** 未关闭账号写入新的 Vault 配置引用；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateVaultToken(ChannelAccount account, String token, Instant now);

    /** 以原对象当前状态为条件变更状态；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateStatus(ChannelAccount account, ChannelStatus toStatus, Instant now);

    /** 统计渠道下未关闭的账号数。 */
    long countOpenByChannel(long channelId);

    /** 按渠道、OE 与状态分页读取；各条件为空表示不筛选。 */
    PagingResponseBody<ChannelAccount> findPage(Long channelId, String entityNo, ChannelStatus status,
        PagingRequestBody paging);

    /** 统计渠道下 ACTIVE 账号数。 */
    long countActiveByChannel(long channelId);

    /** 读取渠道下的 ACTIVE 账号，按主键排序。 */
    List<ChannelAccount> findActiveByChannel(long channelId);

    /** 按渠道统计全部账号数（含已关闭）；空集合返回空映射，没有记录的渠道不出现。 */
    Map<Long, Long> countByChannels(Collection<Long> channelIds);
}
