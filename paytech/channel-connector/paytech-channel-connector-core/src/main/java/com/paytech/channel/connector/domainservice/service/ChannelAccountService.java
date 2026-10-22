package com.paytech.channel.connector.domainservice.service;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelAccountContext;
import com.paytech.channel.connector.domain.ChannelAccountDetailView;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

/**
 * 渠道技术账号的本地保存与查询
 * <li>每个写方法在一个事务内完成账号写入与变更记录插入；只操作本服务数据库，不调用 Vault 或其他外部依赖。</li>
 */
public interface ChannelAccountService {

    /** 读取账号；不存在时业务拒绝。 */
    ChannelAccount loadAccount(long channelAccountId);

    /** 账号详情与当前绑定且未关闭的 MID 数；不存在时业务拒绝。 */
    ChannelAccountDetailView getAccount(long channelAccountId);

    /** 分页查询账号；各条件为空表示不筛选。 */
    PagingResponseBody<ChannelAccount> listAccounts(Long channelId, String entityNo, ChannelStatus status,
        PagingRequestBody paging);

    /** 一个事务内写入新账号与 CREATE 变更记录。 */
    void createAccount(ChannelAccountContext context);

    /** 一个事务内以未关闭为条件修改名称与备注，并写入 UPDATE 变更记录。 */
    void updateProfile(ChannelAccountContext context, String accountName, String remark);

    /**
     * 一个事务内以未关闭为条件写入 Vault Protect 已返回的 Token，并写入 CONFIGURATION 变更记录
     * <li>变更记录不含 Token、参数或凭据。</li>
     */
    void updateVaultToken(ChannelAccountContext context, String token);

    /** 一个事务内以原状态为条件变更状态并写入 STATUS 变更记录。 */
    void changeAccountStatus(ChannelAccountContext context, ChannelStatus targetStatus);
}
