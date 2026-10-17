package com.paytech.risk.test.builder.evaluation;

import com.paytech.risk.api.evaluation.request.ManualPayoutEligibilityRequestBody;

/**
 * Manual Payout 资格评估请求的测试 Builder
 * <li>模板提供合同中合法且 inputHash 已核对的输入；用例只覆盖与场景相关的字段。</li>
 * <li>build() 不修复非法值，也不重新计算 inputHash，负例保持用例设置的原样。</li>
 * <li>摘要向量在生产代码之外按合同标准输入（键名字典序 JSON + SHA-256）独立计算，不借用被测实现。</li>
 */
public final class ManualPayoutEligibilityRequestBuilder {
    /** Client 在 PAYOUT_CREATION 检查点、以本模板默认值计算的标准输入摘要。 */
    public static final String CLIENT_CREATION_HASH =
        "22f478943a9622e52a408d8234b306b6dbbfb41304187039dd9752d09b4356fb";

    /** Local Partner 在 PAYOUT_FINAL_APPROVAL 检查点、来源版本 8 时的标准输入摘要。 */
    public static final String PARTNER_FINAL_APPROVAL_HASH =
        "af45f2041ff238b156cb5afb0308b36039854a9c363a26b6514454728b171b2a";

    /** Client 在 PAYOUT_EXECUTION_CREATION 检查点、来源版本 1 时的标准输入摘要。 */
    public static final String CLIENT_EXECUTION_CREATION_HASH =
        "12850d14613f912687578eeaae40bcd48499c4ff707d6a26a7ebadcbe8eed03a";

    /** Local Partner 在 PAYOUT_EXTERNAL_SUBMISSION 检查点、来源版本 3 时的标准输入摘要。 */
    public static final String PARTNER_EXTERNAL_SUBMISSION_HASH =
        "974613116f0fae5d08f1a24384405d9a7581d8b9d6217dfc7ca957b3572a4b8f";

    private final ManualPayoutEligibilityRequestBody body = new ManualPayoutEligibilityRequestBody();

    /** 仅通过静态模板创建。 */
    private ManualPayoutEligibilityRequestBuilder() {}

    /** Client 创建 Payout 检查点的合法请求。 */
    public static ManualPayoutEligibilityRequestBuilder clientCreation() {
        return new ManualPayoutEligibilityRequestBuilder().commandKey("cmd-client-creation")
            .checkpointType("PAYOUT_CREATION").roleType("CLIENT").roleId("40001").operatingEntityId("50001")
            .grossAmount("102").currency("AED").bankCountry("AE").sourceFactType("PAYOUT").sourceFactId("92001")
            .sourceFactVersion(1).inputHash(CLIENT_CREATION_HASH);
    }

    /** Local Partner 最终审批检查点的合法请求。 */
    public static ManualPayoutEligibilityRequestBuilder localPartnerFinalApproval() {
        return clientCreation().commandKey("cmd-partner-final-approval").checkpointType("PAYOUT_FINAL_APPROVAL")
            .roleType("LOCAL_PARTNER").sourceFactVersion(8).inputHash(PARTNER_FINAL_APPROVAL_HASH);
    }

    /** Client 创建 Payout Execution 检查点的合法请求。 */
    public static ManualPayoutEligibilityRequestBuilder clientExecutionCreation() {
        return clientCreation().commandKey("cmd-client-execution-creation").checkpointType("PAYOUT_EXECUTION_CREATION")
            .sourceFactType("PAYOUT_EXECUTION").inputHash(CLIENT_EXECUTION_CREATION_HASH);
    }

    /** Local Partner 外部提交检查点的合法请求。 */
    public static ManualPayoutEligibilityRequestBuilder localPartnerExternalSubmission() {
        return clientCreation().commandKey("cmd-partner-external-submission")
            .checkpointType("PAYOUT_EXTERNAL_SUBMISSION").roleType("LOCAL_PARTNER").sourceFactType("PAYOUT_EXECUTION")
            .sourceFactVersion(3).inputHash(PARTNER_EXTERNAL_SUBMISSION_HASH);
    }

    /** 设置调用方命令键。 */
    public ManualPayoutEligibilityRequestBuilder commandKey(String value) {
        body.setCommandKey(value);
        return this;
    }

    /** 设置出款检查点。 */
    public ManualPayoutEligibilityRequestBuilder checkpointType(String value) {
        body.setCheckpointType(value);
        return this;
    }

    /** 设置出款角色类型。 */
    public ManualPayoutEligibilityRequestBuilder roleType(String value) {
        body.setRoleType(value);
        return this;
    }

    /** 设置出款角色 ID。 */
    public ManualPayoutEligibilityRequestBuilder roleId(String value) {
        body.setRoleId(value);
        return this;
    }

    /** 设置 Operating Entity ID。 */
    public ManualPayoutEligibilityRequestBuilder operatingEntityId(String value) {
        body.setOperatingEntityId(value);
        return this;
    }

    /** 设置出款总额。 */
    public ManualPayoutEligibilityRequestBuilder grossAmount(String value) {
        body.setGrossAmount(value);
        return this;
    }

    /** 设置币种。 */
    public ManualPayoutEligibilityRequestBuilder currency(String value) {
        body.setCurrency(value);
        return this;
    }

    /** 设置收款银行账户国家。 */
    public ManualPayoutEligibilityRequestBuilder bankCountry(String value) {
        body.setBankCountry(value);
        return this;
    }

    /** 设置来源事实类型。 */
    public ManualPayoutEligibilityRequestBuilder sourceFactType(String value) {
        body.setSourceFactType(value);
        return this;
    }

    /** 设置来源事实 ID。 */
    public ManualPayoutEligibilityRequestBuilder sourceFactId(String value) {
        body.setSourceFactId(value);
        return this;
    }

    /** 设置来源事实版本。 */
    public ManualPayoutEligibilityRequestBuilder sourceFactVersion(Integer value) {
        body.setSourceFactVersion(value);
        return this;
    }

    /** 设置标准输入摘要。 */
    public ManualPayoutEligibilityRequestBuilder inputHash(String value) {
        body.setInputHash(value);
        return this;
    }

    /** 返回独立的请求对象，后续修改 Builder 不影响已构造的请求。 */
    public ManualPayoutEligibilityRequestBody build() {
        var copy = new ManualPayoutEligibilityRequestBody();
        copy.setCommandKey(body.getCommandKey());
        copy.setCheckpointType(body.getCheckpointType());
        copy.setRoleType(body.getRoleType());
        copy.setRoleId(body.getRoleId());
        copy.setOperatingEntityId(body.getOperatingEntityId());
        copy.setGrossAmount(body.getGrossAmount());
        copy.setCurrency(body.getCurrency());
        copy.setBankCountry(body.getBankCountry());
        copy.setSourceFactType(body.getSourceFactType());
        copy.setSourceFactId(body.getSourceFactId());
        copy.setSourceFactVersion(body.getSourceFactVersion());
        copy.setInputHash(body.getInputHash());
        return copy;
    }
}
