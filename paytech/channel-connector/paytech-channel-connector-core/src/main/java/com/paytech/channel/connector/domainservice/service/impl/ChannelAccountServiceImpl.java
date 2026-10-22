package com.paytech.channel.connector.domainservice.service.impl;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelAccountContext;
import com.paytech.channel.connector.domain.ChannelAccountDetailView;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 渠道技术账号的本地保存与查询；写入方法加入调用方 Processor 的事务，变更记录由 Processor 在同一事务中写入。 */
@Service
@AllArgsConstructor
public class ChannelAccountServiceImpl implements ChannelAccountService {
    private final ChannelMidRepository channelMidRepository;
    private final ChannelAccountRepository channelAccountRepository;

    /** 读取账号；不存在时业务拒绝。 */
    @Override
    public ChannelAccount loadAccount(long channelAccountId) {
        ChannelAccount account = channelAccountRepository.findById(channelAccountId);
        if (account == null) {
            throw new BizCheckFailException("渠道技术账号不存在: " + channelAccountId);
        }
        return account;
    }

    /** 账号详情与绑定 MID 数。 */
    @Override
    public ChannelAccountDetailView getAccount(long channelAccountId) {
        ChannelAccount account = loadAccount(channelAccountId);
        return new ChannelAccountDetailView(account, channelMidRepository.countOpenByAccount(channelAccountId));
    }

    /** 分页查询账号。 */
    @Override
    public PagingResponseBody<ChannelAccount> listAccounts(Long channelId, String entityNo, ChannelStatus status,
        PagingRequestBody paging) {
        return channelAccountRepository.findPage(channelId, entityNo, status, paging);
    }

    /** 写入新账号；须在调用方事务内执行。 */
    @Override
    public void createAccount(ChannelAccountContext context) {
        channelAccountRepository.insert(context.getAccount());
    }

    /** 修改名称与备注；须在调用方事务内执行。 */
    @Override
    public void updateProfile(ChannelAccountContext context, String accountName, String remark) {
        channelAccountRepository.updateProfile(context.getAccount(), accountName, remark, context.getNow());
    }

    /** 写入新 Token ；须在调用方事务内执行。 */
    @Override
    public void updateVaultToken(ChannelAccountContext context, String token) {
        channelAccountRepository.updateVaultToken(context.getAccount(), token, context.getNow());
    }

    /** 变更状态；须在调用方事务内执行。 */
    @Override
    public void changeAccountStatus(ChannelAccountContext context, ChannelStatus targetStatus) {
        channelAccountRepository.updateStatus(context.getAccount(), targetStatus, context.getNow());
    }
}
