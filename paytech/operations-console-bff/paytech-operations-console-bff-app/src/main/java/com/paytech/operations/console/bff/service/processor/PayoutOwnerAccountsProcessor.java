package com.paytech.operations.console.bff.service.processor;

import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.PayoutClient;
import com.paytech.operations.console.bff.service.model.payout.response.PayoutOwnerAccountsResponseBody;
import com.paytech.operations.console.bff.service.processor.request.PayoutOwnerAccountsRequest;
import com.paytech.payout.api.beneficiary.response.BeneficiaryAccountResponseBody;
import com.paytech.payout.api.enums.BeneficiaryActorType;

import lombok.AllArgsConstructor;

/**
 * Owner 账户与余额页的账户部分：payout 账户列表 + Owner 主档摘要 + 运营操作人显示名
 * <li>payout 失败整页失败；主档或显示名读取失败只降级为空，不伪造。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PayoutOwnerAccountsProcessor extends AbstractProcessor<PayoutOwnerAccountsRequest, PayoutOwnerAccountsResponseBody> {
    private final PayoutClient payout;
    private final PayoutOwnerResolver owners;
    private final OperatorLabelResolver labels;

    @Override
    protected PayoutOwnerAccountsResponseBody processRequest(PayoutOwnerAccountsRequest request) {
        var items = payout.listAccounts(request.getOwnerType(), request.getOwnerId(), request.getStatus(),
            request.getCurrency());
        String key = PayoutOwnerResolver.key(request.getOwnerType(), request.getOwnerId());
        var owner = owners.resolve(request.getOperator(), Stream.of(key)).get(key);
        var operatorLabels = labels.resolve(items.stream().flatMap(PayoutOwnerAccountsProcessor::operators));
        return new PayoutOwnerAccountsResponseBody(request.getOwnerType(), request.getOwnerId(), owner, items, operatorLabels);
    }

    /** 账户上的运营操作人：Maker、Checker 与运营提交人（Client 成员不是运营账号，不查询）。 */
    static Stream<String> operators(BeneficiaryAccountResponseBody account) {
        String submitter = BeneficiaryActorType.OPERATIONS.getCode().equals(account.getSubmittedByType())
            ? account.getSubmittedBy() : null;
        return Stream.of(account.getReviewMakerId(), account.getCheckerId(), submitter);
    }
}
