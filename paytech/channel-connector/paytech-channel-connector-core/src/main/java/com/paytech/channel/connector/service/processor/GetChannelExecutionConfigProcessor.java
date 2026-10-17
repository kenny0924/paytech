package com.paytech.payment.channel.service.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.paytech.payment.channel.core.configuration.ChannelProperties;
import com.paytech.payment.channel.domain.ChannelExecutionConfigView;
import com.paytech.payment.channel.domainservice.repository.*;
import com.paytech.payment.channel.integration.ChannelAccountConfigReader;
import com.paytech.payment.channel.service.processor.request.GetChannelExecutionConfigRequest;

/** 受控执行边界的配置入口；原交易坐标必须由调用方从权威交易事实取得。 */
@Component
public class GetChannelExecutionConfigProcessor
    extends AbstractChannelExecutionProcessor<GetChannelExecutionConfigRequest, ChannelExecutionConfigView> {
    private final ChannelAccountConfigReader configReader;

    /** 注入冻结坐标校验与 Vault 账号读取。 */
    public GetChannelExecutionConfigProcessor(ChannelProperties properties, ChannelMidRepository mids,
        MidCapabilityRepository capabilities, ChannelAccountRepository accounts, PaymentChannelRepository channels,
        ChannelAccountConfigReader configReader, PlatformTransactionManager transactionManager,
        MidCapabilityDetailRepository details) {
        super(properties, mids, capabilities, accounts, channels, transactionManager, details);
        this.configReader = configReader;
    }

    /** 本请求即冻结坐标。 */
    @Override
    protected GetChannelExecutionConfigRequest target(GetChannelExecutionConfigRequest request) {
        return request;
    }

    /** 本地一致读取结束后才 Reveal；不持有跨网络事务、配置锁或明文缓存。 */
    @Override
    protected ChannelExecutionConfigView processRequest(GetChannelExecutionConfigRequest request) {
        var context = readChecked(request);
        var accountConfiguration = configReader.read(context);
        var result = new ChannelExecutionConfigView();
        result.setChannelCode(context.getChannel().getChannelCode());
        result.setExternalMidCode(context.getMid().getExternalMidCode());
        result.setAccountConfiguration(accountConfiguration);
        return result;
    }
}
