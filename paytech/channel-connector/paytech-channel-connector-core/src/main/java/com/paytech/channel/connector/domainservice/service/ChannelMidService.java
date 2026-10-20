package com.paytech.channel.connector.domainservice.service;

import com.paytech.channel.connector.core.dal.dataobject.ChannelMid;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelMidContext;
import com.paytech.channel.connector.domain.ChannelMidDetailView;

/**
 * MID 的本地保存与查询
 * <li>每个写方法在一个事务内完成 MID 写入与变更记录插入；只操作本服务数据库。</li>
 */
public interface ChannelMidService {

    /** 读取 MID；不存在时业务拒绝。 */
    ChannelMid loadMid(long midId);

    /** MID 详情：MID 与全部能力组、明细；不存在时业务拒绝。 */
    ChannelMidDetailView getMid(long midId);

    /** 一个事务内写入新 MID 与 CREATE 变更记录；同渠道外部 MID 重复按并发冲突拒绝。 */
    void createMid(ChannelMidContext context);

    /** 一个事务内以原绑定与未关闭为条件改绑到上下文中的目标账号，并写入 REBIND 变更记录。 */
    void rebindAccount(ChannelMidContext context);

    /** 一个事务内以原状态为条件变更状态、写入激活 / 关闭时间，并写入 STATUS 变更记录。 */
    void changeMidStatus(ChannelMidContext context, ChannelStatus targetStatus);
}
