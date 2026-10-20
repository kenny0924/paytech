package com.paytech.channel.connector.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelSummaryView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/** PaymentChannel 单表只读仓储。 */
public interface PaymentChannelRepository {
    /** 按明确归属批量读取配置；空 ID 集合返回空集合。 */
    List<PaymentChannel> findByIds(List<Long> ids);

    /** 按内部主键读取，未找到返回空。 */
    PaymentChannel findById(long id);

    // ---- CC-B 追加：渠道管理写入与分页 ----

    /** 写入新渠道；渠道编码重复按并发冲突拒绝。 */
    void insert(PaymentChannel channel);

    /** 未关闭渠道改名；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateName(PaymentChannel channel, String channelName, Instant now);

    /** 以原对象当前状态为条件变更状态；行数不为 1 按并发冲突拒绝，成功后同步原对象。 */
    void updateStatus(PaymentChannel channel, ChannelStatus toStatus, Instant now);

    /** 按状态与编码 / 名称前缀分页读取列表项；数据库 LIMIT/OFFSET 与 COUNT 使用相同条件。 */
    PagingResponseBody<ChannelSummaryView> findSummaries(ChannelStatus status, String keyword,
        PagingRequestBody paging);
}
