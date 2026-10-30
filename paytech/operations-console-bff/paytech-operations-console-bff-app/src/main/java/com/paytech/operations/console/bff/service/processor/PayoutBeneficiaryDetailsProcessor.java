package com.paytech.operations.console.bff.service.processor;

import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.PayoutClient;
import com.paytech.operations.console.bff.service.model.payout.response.PayoutBeneficiaryDetailResponseBody;
import com.paytech.operations.console.bff.service.processor.request.PayoutBeneficiaryDetailsRequest;
import com.paytech.payout.api.enums.BeneficiaryActorType;

import lombok.AllArgsConstructor;

/**
 * 收款账户核验详情：payout 账户与事件 + Owner 法定名（S-02）+ 共用同一账号的其他 Owner 名称 + 运营操作人显示名
 * <li>payout 失败（含 404）整页失败；主档或显示名读取失败只降级为空，户名对照显示为不可用。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PayoutBeneficiaryDetailsProcessor
    extends AbstractProcessor<PayoutBeneficiaryDetailsRequest, PayoutBeneficiaryDetailResponseBody> {
    private final PayoutClient payout;
    private final PayoutOwnerResolver owners;
    private final OperatorLabelResolver labels;

    @Override
    protected PayoutBeneficiaryDetailResponseBody processRequest(PayoutBeneficiaryDetailsRequest request) {
        var account = payout.getAccount(request.getAccountId());
        var resolved = owners.resolve(request.getOperator(), PayoutOwnerResolver.keys(account));
        var owner = resolved.get(PayoutOwnerResolver.key(account.getOwnerType(), account.getOwnerId()));
        var eventOperators = account.getEvents().stream()
            .filter(event -> BeneficiaryActorType.OPERATIONS.getCode().equals(event.getActorType()))
            .map(event -> event.getActorId());
        var operatorLabels = labels.resolve(Stream.concat(PayoutOwnerAccountsProcessor.operators(account), eventOperators));
        return new PayoutBeneficiaryDetailResponseBody(account, owner, PayoutOwnerResolver.sharedProfiles(account, resolved),
            operatorLabels);
    }
}
