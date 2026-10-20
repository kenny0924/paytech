package com.paytech.channel.connector.domainservice.service;

import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelContext;
import com.paytech.channel.connector.domain.ChannelSummaryView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 支付渠道的本地保存与查询
 * <li>每个写方法在一个事务内完成渠道写入与变更记录插入；只操作本服务数据库，不调用外部依赖。</li>
 */
public interface PaymentChannelService {

    /** 读取渠道；不存在时业务拒绝。 */
    PaymentChannel loadChannel(long channelId);

    /** 分页查询渠道列表项。 */
    PagingResponseBody<ChannelSummaryView> listChannels(ChannelStatus status, String keyword, PagingRequestBody paging);

    /** 一个事务内写入新渠道与 CREATE 变更记录；渠道编码重复按并发冲突拒绝。 */
    void createChannel(ChannelContext context);

    /** 一个事务内以未关闭为条件改名并写入 UPDATE 变更记录。 */
    void renameChannel(ChannelContext context, String channelName);

    /** 一个事务内以原状态为条件变更状态并写入 STATUS 变更记录。 */
    void changeChannelStatus(ChannelContext context, ChannelStatus targetStatus);
}
