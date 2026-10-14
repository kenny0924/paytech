package com.paytech.payment.channel.service.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.payment.channel.core.configuration.ChannelProperties;
import com.paytech.payment.channel.domain.ChannelExecutionContext;
import com.paytech.payment.channel.domain.ChannelOperationResult;
import com.paytech.payment.channel.domainservice.repository.*;
import com.paytech.payment.channel.integration.PlatformStandardApiClient;
import com.paytech.payment.channel.service.processor.request.ExecuteChannelOperationRequest;
import com.paytech.payment.channel.service.processor.request.GetChannelExecutionConfigRequest;

/**
 * 经 Platform 执行一次渠道操作（PayerMax 卡直付对接技术方案第 5.1 节）：按用途校验冻结的 Channel/Account/MID 后，
 * 把账号身份与 Vault 引用交给 Platform，由 Platform 读取账号与卡数据并执行渠道协议；Payment 不 Reveal。
 *
 * <p>
 * Platform 接入方编码沿用渠道编码（channel_code，例如 PAYERMAX）。冻结坐标不可用时不发出请求，返回 NOT_SENT。 本入口无数据库写入，在调用方事务之外执行。
 */
@Component
public class ExecuteChannelOperationProcessor
    extends AbstractChannelExecutionProcessor<ExecuteChannelOperationRequest, ChannelOperationResult> {
    private final PlatformStandardApiClient platformStandardApiClient;

    /** 注入冻结坐标校验与 Platform 执行客户端。 */
    public ExecuteChannelOperationProcessor(ChannelProperties properties, ChannelMidRepository mids,
        MidCapabilityRepository capabilities, ChannelAccountRepository accounts, PaymentChannelRepository channels,
        PlatformTransactionManager transactionManager, MidCapabilityDetailRepository details,
        PlatformStandardApiClient platformStandardApiClient) {
        super(properties, mids, capabilities, accounts, channels, transactionManager, details);
        this.platformStandardApiClient = platformStandardApiClient;
    }

    /** 冻结坐标在请求的 target 中。 */
    @Override
    protected GetChannelExecutionConfigRequest target(ExecuteChannelOperationRequest request) {
        return request.getTarget();
    }

    /** 校验冻结坐标（本地一致读取）后在事务外调用 Platform；坐标不可用时不发出请求。 */
    @Override
    protected ChannelOperationResult processRequest(ExecuteChannelOperationRequest request) {
        ChannelExecutionContext context;
        try {
            context = readChecked(request.getTarget());
        } catch (BizCheckFailException unavailable) {
            return ChannelOperationResult.notSent("CHANNEL_TARGET_UNAVAILABLE");
        }
        if (context.getAccount().getAccountVaultToken() == null) {
            return ChannelOperationResult.notSent("CHANNEL_ACCOUNT_NOT_CONFIGURED");
        }
        String integrationCode = context.getChannel().getChannelCode();
        return platformStandardApiClient.execute(request.getStandardApiCode(), integrationCode, input(context, request));
    }

    /** 业务输入加上冻结账号的身份与 Vault 引用。 */
    static Map<String, Object> input(ChannelExecutionContext context, ExecuteChannelOperationRequest request) {
        var account = new LinkedHashMap<String, Object>();
        account.put("channelCode", context.getChannel().getChannelCode());
        account.put("channelAccountId", context.getAccount().getChannelAccountId().toString());
        account.put("entityNo", context.getAccount().getEntityNo());
        account.put("accountVaultToken", context.getAccount().getAccountVaultToken());
        var input = new LinkedHashMap<>(request.getInput());
        input.put("channelAccount", account);
        return input;
    }
}
