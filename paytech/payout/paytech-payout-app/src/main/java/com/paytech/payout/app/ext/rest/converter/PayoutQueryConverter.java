package com.paytech.payout.app.ext.rest.converter;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody;
import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody.BalancePosition;
import com.paytech.payout.api.balance.response.BalanceExplanationResponseBody.UsableAccountSummary;
import com.paytech.payout.api.beneficiary.response.BeneficiaryRequirementResponseBody;
import com.paytech.payout.api.beneficiary.response.BeneficiaryRequirementResponseBody.BeneficiaryFieldRequirement;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.core.configuration.BeneficiaryRequirementProperties.FieldRequirement;
import com.paytech.payout.core.configuration.BeneficiaryRequirementProperties.RouteRequirement;
import com.paytech.payout.domain.BalanceExplanation;
import com.paytech.payout.domain.OwnerBalancePosition;

/** 字段要求与余额解释的 HTTP 协议转换；金额按币种精度输出主单位字符串。 */
@Component
public class PayoutQueryConverter {

    /** 字段要求响应：回显请求的组合（通配国家条目也回显具体国家）。 */
    public BeneficiaryRequirementResponseBody toRequirementBody(String railType, String bankCountry, String currency,
        RouteRequirement route) {
        BeneficiaryRequirementResponseBody body = new BeneficiaryRequirementResponseBody();
        body.setRailType(railType);
        body.setBankCountry(bankCountry);
        body.setCurrency(currency);
        body.setFields(route.getFields().stream().map(PayoutQueryConverter::toField).toList());
        return body;
    }

    /** 余额解释响应。 */
    public BalanceExplanationResponseBody toBalanceBody(BeneficiaryOwnerType ownerType, Long ownerId,
        BalanceExplanation explanation) {
        BalanceExplanationResponseBody body = new BalanceExplanationResponseBody();
        body.setOwnerType(ownerType.getCode());
        body.setOwnerId(ownerId.toString());
        body.setPositions(explanation.getPositions().stream().map(PayoutQueryConverter::toPosition).toList());
        body.setUsableAccounts(explanation.getUsableAccounts().stream().map(PayoutQueryConverter::toUsable).toList());
        return body;
    }

    /** 一个字段要求。 */
    static BeneficiaryFieldRequirement toField(FieldRequirement field) {
        BeneficiaryFieldRequirement body = new BeneficiaryFieldRequirement();
        body.setCode(field.getCode());
        body.setRequired(field.isRequired());
        body.setOneOfGroup(field.getOneOfGroup());
        body.setPattern(field.getPattern());
        body.setMaxLength(field.getMaxLength());
        body.setLabelKey(field.getLabelKey());
        return body;
    }

    /** 一个余额位置。 */
    static BalancePosition toPosition(BalanceExplanation.PositionExplanation explained) {
        OwnerBalancePosition position = explained.getPosition();
        String currency = position.getCurrency();
        BalancePosition body = new BalancePosition();
        body.setEntityNo(position.getEntityNo());
        body.setCurrency(currency);
        body.setPending(PayoutWire.amount(currency, position.getPending()));
        body.setAvailable(PayoutWire.amount(currency, position.getAvailable()));
        body.setLocked(PayoutWire.amount(currency, position.getLocked()));
        body.setSecurityDeposit(PayoutWire.amount(currency, position.getSecurityDeposit()));
        BigDecimal recovery = position.getRecoveryReceivable();
        body.setRecoveryReceivable(PayoutWire.amount(currency, recovery));
        body.setBlockReasons(explained.getBlockReasons().stream().map(reason -> reason.getCode()).toList());
        return body;
    }

    /** 一个币种的可用账户统计。 */
    static UsableAccountSummary toUsable(BalanceExplanation.UsableAccounts usable) {
        UsableAccountSummary body = new UsableAccountSummary();
        body.setCurrency(usable.getCurrency());
        body.setCount(usable.getCount());
        body.setDefaultAccountId(PayoutWire.text(usable.getDefaultAccountId()));
        return body;
    }
}
