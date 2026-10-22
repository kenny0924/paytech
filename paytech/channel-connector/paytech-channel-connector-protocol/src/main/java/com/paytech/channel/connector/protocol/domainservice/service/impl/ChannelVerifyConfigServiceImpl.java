package com.paytech.channel.connector.protocol.domainservice.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyCase;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyCaseRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelVerifyConfigService;

import lombok.AllArgsConstructor;

/** 验证账号与用例的本地保存，版本保护由 Repository 条件更新完成。 */
@Service
@AllArgsConstructor
public class ChannelVerifyConfigServiceImpl implements ChannelVerifyConfigService {

    private final ChannelVerifyAccountRepository accounts;
    private final ChannelVerifyCaseRepository cases;

    /** 插入新账号，渠道内编码唯一索引是最终保护。 */
    @Override
    public void createAccount(ChannelVerifyAccount account) {
        accounts.insert(account);
    }

    /** 按原版本整体更新账号可编辑字段。 */
    @Override
    public void updateAccount(ChannelVerifyAccount account, String name, String channelCode,
        String channelAccountId, String entityNo, String parametersJson, String credentialsJson, long updatedBy,
        Instant updateTime) {
        accounts.update(account, name, channelCode, channelAccountId, entityNo, parametersJson, credentialsJson,
            updatedBy, updateTime);
    }

    /** 按原版本删除账号。 */
    @Override
    public void deleteAccount(ChannelVerifyAccount account) {
        accounts.delete(account);
    }

    /** 插入新用例，渠道内编码唯一索引是最终保护。 */
    @Override
    public void createCase(ChannelVerifyCase verifyCase) {
        cases.insert(verifyCase);
    }

    /** 按原版本整体更新用例可编辑字段。 */
    @Override
    public void updateCase(ChannelVerifyCase verifyCase, String name, String description, long verifyAccountId,
        String stepsJson, long updatedBy, Instant updateTime) {
        cases.update(verifyCase, name, description, verifyAccountId, stepsJson, updatedBy, updateTime);
    }

    /** 按原版本删除用例。 */
    @Override
    public void deleteCase(ChannelVerifyCase verifyCase) {
        cases.delete(verifyCase);
    }
}
