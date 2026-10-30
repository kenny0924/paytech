package com.paytech.payout.test.builder;

import com.paytech.payout.api.beneficiary.request.BeneficiaryAccountFields;
import com.paytech.payout.api.beneficiary.request.SubmitBeneficiaryAccountRequestBody;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.test.support.PayoutTestConstants;

/**
 * 提交收款账户请求 Builder：template() 为 Client 门户成员提交的 AE 本地 USD 账户（IBAN）
 * <li>只组装请求，不修复负例；允许显式设置 null 或非法值。</li>
 */
public final class SubmitBeneficiaryAccountBuilder {
    private final SubmitBeneficiaryAccountRequestBody body = new SubmitBeneficiaryAccountRequestBody();

    private final BeneficiaryAccountFields fields = new BeneficiaryAccountFields();

    private SubmitBeneficiaryAccountBuilder() {
        body.setFields(fields);
    }

    /** AE 本地 USD、合成 IBAN 与户名，Client 门户成员提交。 */
    public static SubmitBeneficiaryAccountBuilder template() {
        SubmitBeneficiaryAccountBuilder builder = new SubmitBeneficiaryAccountBuilder();
        builder.body.setOwnerType(BeneficiaryOwnerType.CLIENT.getCode());
        builder.body.setOwnerId(String.valueOf(PayoutTestConstants.CLIENT_ID));
        builder.body.setRailType(BeneficiaryRailType.LOCAL.getCode());
        builder.body.setBankCountry("AE");
        builder.body.setCurrency("USD");
        builder.body.setActorType(BeneficiaryActorType.CLIENT_MEMBER.getCode());
        builder.body.setActorId(String.valueOf(PayoutTestConstants.CLIENT_MEMBER_ID));
        builder.fields.setHolderName(PayoutTestConstants.HOLDER_NAME);
        builder.fields.setIban(PayoutTestConstants.AE_IBAN);
        builder.fields.setBankName(PayoutTestConstants.BANK_NAME);
        return builder;
    }

    /** 运营代提交（Owner 不变）。 */
    public SubmitBeneficiaryAccountBuilder byOperations(long actorId) {
        body.setActorType(BeneficiaryActorType.OPERATIONS.getCode());
        body.setActorId(String.valueOf(actorId));
        return this;
    }

    /** Owner。 */
    public SubmitBeneficiaryAccountBuilder owner(BeneficiaryOwnerType ownerType, long ownerId) {
        body.setOwnerType(ownerType.getCode());
        body.setOwnerId(String.valueOf(ownerId));
        return this;
    }

    /** 收款方式、国家与币种。 */
    public SubmitBeneficiaryAccountBuilder route(BeneficiaryRailType railType, String bankCountry, String currency) {
        body.setRailType(railType.getCode());
        body.setBankCountry(bankCountry);
        body.setCurrency(currency);
        return this;
    }

    /** 户名。 */
    public SubmitBeneficiaryAccountBuilder holderName(String holderName) {
        fields.setHolderName(holderName);
        return this;
    }

    /** IBAN。 */
    public SubmitBeneficiaryAccountBuilder iban(String iban) {
        fields.setIban(iban);
        return this;
    }

    /** 账号。 */
    public SubmitBeneficiaryAccountBuilder accountNumber(String accountNumber) {
        fields.setAccountNumber(accountNumber);
        return this;
    }

    /** 本地银行代码。 */
    public SubmitBeneficiaryAccountBuilder bankCode(String bankCode) {
        fields.setBankCode(bankCode);
        return this;
    }

    /** SWIFT BIC。 */
    public SubmitBeneficiaryAccountBuilder swiftBic(String swiftBic) {
        fields.setSwiftBic(swiftBic);
        return this;
    }

    /** 银行名称。 */
    public SubmitBeneficiaryAccountBuilder bankName(String bankName) {
        fields.setBankName(bankName);
        return this;
    }

    /** 构造请求。 */
    public SubmitBeneficiaryAccountRequestBody build() {
        return body;
    }
}
