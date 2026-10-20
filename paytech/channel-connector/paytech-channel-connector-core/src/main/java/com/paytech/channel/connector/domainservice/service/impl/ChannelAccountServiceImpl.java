package com.paytech.channel.connector.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelAccountContext;
import com.paytech.channel.connector.domain.ChannelAccountDetailView;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelConfigChangeRepository;
import com.paytech.channel.connector.domainservice.repository.ChannelMidRepository;
import com.paytech.channel.connector.domainservice.service.ChannelAccountService;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** 渠道技术账号的本地保存与查询；写入与变更记录同事务提交。 */
@Service
@AllArgsConstructor
public class ChannelAccountServiceImpl implements ChannelAccountService {
    private final ChannelMidRepository channelMidRepository;
    private final TransactionTemplate transactionTemplate;
    private final ChannelAccountRepository channelAccountRepository;
    private final ChannelConfigChangeRepository channelConfigChangeRepository;

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

    /** 写入新账号与变更记录。 */
    @Override
    public void createAccount(ChannelAccountContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            channelAccountRepository.insert(context.getAccount());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 修改名称与备注并写入变更记录。 */
    @Override
    public void updateProfile(ChannelAccountContext context, String accountName, String remark) {
        transactionTemplate.executeWithoutResult(status -> {
            channelAccountRepository.updateProfile(context.getAccount(), accountName, remark, context.getNow());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 写入新 Token 并写入变更记录。 */
    @Override
    public void updateVaultToken(ChannelAccountContext context, String token) {
        transactionTemplate.executeWithoutResult(status -> {
            channelAccountRepository.updateVaultToken(context.getAccount(), token, context.getNow());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }

    /** 变更状态并写入变更记录。 */
    @Override
    public void changeAccountStatus(ChannelAccountContext context, ChannelStatus targetStatus) {
        transactionTemplate.executeWithoutResult(status -> {
            channelAccountRepository.updateStatus(context.getAccount(), targetStatus, context.getNow());
            channelConfigChangeRepository.insert(context.getChange());
        });
    }
}
