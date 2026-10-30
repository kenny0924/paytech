package com.paytech.payout.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.payout.api.balance.BalanceExplanationApi;
import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.app.ext.rest.converter.PayoutQueryConverter;
import com.paytech.payout.app.ext.rest.converter.PayoutWire;
import com.paytech.payout.service.processor.ExplainBalanceProcessor;
import com.paytech.payout.service.processor.request.ExplainBalanceRequest;

import lombok.AllArgsConstructor;

/** 余额解释的 HTTP 协议适配。 */
@RestController
@AllArgsConstructor
public class BalanceExplanationController implements BalanceExplanationApi {
    private final PayoutQueryConverter converter;
    private final ExplainBalanceProcessor explainBalanceProcessor;

    /** 查询余额解释。 */
    @Override
    public BalanceExplanationResponseBody explainBalance(String ownerType, String ownerId) {
        BeneficiaryOwnerType type = PayoutWire.parse(BeneficiaryOwnerType.class, ownerType);
        Long id = PayoutWire.id(ownerId);
        return converter.toBalanceBody(type, id, explainBalanceProcessor.process(new ExplainBalanceRequest(type, id)));
    }
}
