package com.paytech.operations.console.bff.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.ChannelConfigurationClient;
import com.paytech.operations.console.bff.integration.ReconciliationClearingClient;
import com.paytech.operations.console.bff.service.model.ChannelModels.ChannelMidResponseBody;
import com.paytech.operations.console.bff.service.model.ReconciliationClearingModels.ClearingAccountResponseBody;
import com.paytech.operations.console.bff.service.processor.request.ReconClearingAccountDetailRequest;

import lombok.AllArgsConstructor;

/**
 * 对账清算账户详情页：reconciliation 清算账户详情 + channel-connector MID 详情（补外部 MID 编码与当前状态）
 * <li>先读清算账户，再按已挂入 MID 并发读取 channel-connector MID 详情；清算账户的 MID 数有限（一个经营主体在一个渠道、一个币种下）。</li>
 * <li>任一子调用失败按原错误整页失败（与渠道成本版本对比相同），不把缺失的 MID 显示成空编码。</li>
 * <li>只读组合，不编排任何写入；MID 是否可挂入由 reconciliation 判断。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class ReconClearingAccountDetailProcessor
    extends AbstractProcessor<ReconClearingAccountDetailRequest, ClearingAccountResponseBody> {
    private final ChannelConfigurationClient channels;
    private final ReconciliationClearingClient clearingAccounts;

    @Override
    protected ClearingAccountResponseBody processRequest(ReconClearingAccountDetailRequest request) {
        ClearingAccountResponseBody account = clearingAccounts.getClearingAccount(request.getClearingAccountId());
        var calls = new ArrayList<Supplier<ChannelMidResponseBody>>();
        account.getMids().forEach(mid -> calls.add(() -> channels.mid(mid.getMidId())));
        List<ChannelMidResponseBody> mids = CommercialConcurrentReads.all(calls);
        for (int i = 0; i < mids.size(); i++) {
            var target = account.getMids().get(i);
            target.setExternalMidCode(mids.get(i).getExternalMidCode());
            target.setMidStatus(mids.get(i).getStatus());
        }
        return account;
    }
}
