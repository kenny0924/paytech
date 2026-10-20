package com.paytech.channel.connector.domainservice.repository.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.mapper.ChannelAccountMapper;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domainservice.repository.ChannelAccountRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;

import lombok.AllArgsConstructor;

/** ChannelAccount 仓储不建立事务、不解释跨表资格。 */
@Repository
@AllArgsConstructor
public class ChannelAccountRepositoryImpl implements ChannelAccountRepository {
    /** 本表 SQL 映射。 */
    private final ChannelAccountMapper mapper;

    /** 读取本表范围内的配置。 */
    @Override
    public List<ChannelAccount> findByEntity(String entityNo) {
        return mapper.findByEntity(entityNo);
    }

    /** 读取指定记录，不把缺失解释为默认配置。 */
    @Override
    public ChannelAccount findById(long id) {
        return mapper.findById(id);
    }

    /** 写入新账号；主键由调用方分配，行数不为 1 属于系统错误。 */
    @Override
    public void insert(ChannelAccount account) {
        if (mapper.insert(account) != 1) {
            throw new ErrorException("渠道技术账号写入失败: " + account.getChannelAccountId());
        }
    }

    /** 未关闭账号修改名称与备注，成功后同步原对象。 */
    @Override
    public void updateProfile(ChannelAccount account, String accountName, String remark, Instant now) {
        if (mapper.updateProfile(account.getChannelAccountId(), accountName, remark, now) != 1) {
            throw new ConcurrentRequestException("渠道技术账号已关闭或已变化: " + account.getChannelAccountId());
        }
        account.setAccountName(accountName);
        account.setRemark(remark);
        account.setUpdateTime(now);
    }

    /** 未关闭账号写入新的 Vault 配置引用，成功后同步原对象；异常信息不含 Token。 */
    @Override
    public void updateVaultToken(ChannelAccount account, String token, Instant now) {
        if (mapper.updateVaultToken(account.getChannelAccountId(), token, now) != 1) {
            throw new ConcurrentRequestException("渠道技术账号已关闭或已变化: " + account.getChannelAccountId());
        }
        account.setAccountVaultToken(token);
        account.setUpdateTime(now);
    }

    /** 以原状态为条件变更状态，成功后同步原对象。 */
    @Override
    public void updateStatus(ChannelAccount account, ChannelStatus toStatus, Instant now) {
        if (mapper.updateStatus(account.getChannelAccountId(), account.getAccountStatus(), toStatus, now) != 1) {
            throw new ConcurrentRequestException("渠道技术账号状态已变化: " + account.getChannelAccountId());
        }
        account.setAccountStatus(toStatus);
        account.setUpdateTime(now);
    }

    /** 统计渠道下未关闭的账号数。 */
    @Override
    public long countOpenByChannel(long channelId) {
        return mapper.countOpenByChannel(channelId);
    }

    /** 分页读取；无记录时不再查询列表。 */
    @Override
    public PagingResponseBody<ChannelAccount> findPage(Long channelId, String entityNo, ChannelStatus status,
        PagingRequestBody paging) {
        long total = mapper.countPage(channelId, entityNo, status);
        List<ChannelAccount> items = total == 0 ? List.of()
            : mapper.selectPage(channelId, entityNo, status, paging.offset(), paging.size());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), total);
    }
}
