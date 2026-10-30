package com.paytech.risk.test.builder.control;

import java.util.Arrays;
import java.util.List;

import com.paytech.risk.decision.api.RiskVariableModels.MemberRequest;
import com.paytech.risk.decision.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.decision.api.request.CreateVariableRequestBody;
import com.paytech.risk.decision.api.request.ReplaceMembersRequestBody;
import com.paytech.risk.test.builder.decision.VariableRequestBuilder;

/**
 * 档位额度风控变量的请求体：按档位配置说明的命名约定构造常量与币种集合
 * <li>额度常量为支付决策点的 MONEY 常量，币种来源为支付币种输入变量（规则以“支付币种 = {CCY}”限定）；也可构造固定币种常量。</li>
 * <li>币种集合为全局 STRING 集合，格式与支付币种一致。只构造对象，不访问数据库或网络。</li>
 */
public final class TierQuotaVariableBuilder {
    /** 支付币种输入变量。 */
    public static final String PAY_CURRENCY = "PAY_INPUT_CURRENCY";

    /** 纯构造工具。 */
    private TierQuotaVariableBuilder() { }

    /** 以支付币种为币种来源的额度常量 TIER_{TIER}_{CCY}_{PERIOD}。 */
    public static CreateVariableRequestBody quota(String code, String amount) {
        CreateVariableRequestBody body = base(code, "CONSTANT", "NUMBER", PaymentAttemptRiskInput.POINT_CODE);
        body.setNumericUnit("MONEY");
        body.setCurrencyVariableCode(PAY_CURRENCY);
        body.setCurrentValue(amount);
        return body;
    }

    /** 固定币种的额度常量。 */
    public static CreateVariableRequestBody fixedQuota(String code, String currency, String amount) {
        CreateVariableRequestBody body = base(code, "CONSTANT", "NUMBER", PaymentAttemptRiskInput.POINT_CODE);
        body.setNumericUnit("MONEY");
        body.setCurrencyCode(currency);
        body.setCurrentValue(amount);
        return body;
    }

    /** 全局币种集合 TIER_{TIER}_CURRENCIES（成员另行替换）。 */
    public static CreateVariableRequestBody currencySet(String code) {
        CreateVariableRequestBody body = base(code, "COLLECTION", "STRING", null);
        body.setFormatCode("UPPER_ALPHA_3");
        return body;
    }

    /** 币种集合的成员替换请求（新建集合根版本为 1）。 */
    public static ReplaceMembersRequestBody currencies(String... currencies) {
        List<MemberRequest> members = Arrays.stream(currencies)
            .map(currency -> VariableRequestBuilder.member(currency, currency)).toList();
        return VariableRequestBuilder.members("1", members.toArray(MemberRequest[]::new));
    }

    /** 变量公共结构：规则与流程条件用途、ACTIVE。 */
    static CreateVariableRequestBody base(String code, String sourceType, String valueType, String pointCode) {
        CreateVariableRequestBody body = new CreateVariableRequestBody();
        body.setVariableCode(code);
        body.setVariableName(code);
        body.setSourceType(sourceType);
        body.setValueType(valueType);
        body.setPointCode(pointCode);
        body.setUsages(List.of("RULE_CONDITION", "FLOW_CONDITION"));
        body.setFormatCode("NONE");
        body.setStatus("ACTIVE");
        return body;
    }
}
