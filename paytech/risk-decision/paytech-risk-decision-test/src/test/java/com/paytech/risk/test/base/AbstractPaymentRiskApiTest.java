package com.paytech.risk.test.base;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.risk.decision.api.request.CreateVariableRequestBody;
import com.paytech.risk.decision.api.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.decision.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.mapper.evaluation.PaymentRiskTestMapper;
import com.paytech.risk.test.mocker.evaluation.PaymentRiskMocker;

/**
 * 支付风控接口测试的公共入口辅助
 * <li>评估场景经真实配置入口准备规则：变量、名单、规则集（冻结）、决策流与执行发布（校验并激活），再调用支付评估入口；通用入口辅助见 {@link AbstractRuntimeRiskApiTest}。</li>
 * <li>不含 @Test；全部数据由 {@link PaymentRiskMocker} 在全局 AfterEach 回收。</li>
 *
 * @author Kenny
 */
public abstract class AbstractPaymentRiskApiTest extends AbstractRuntimeRiskApiTest {
    /** 支付决策点。 */
    protected static final String PAYMENT = PaymentAttemptRiskInput.POINT_CODE;

    @Autowired
    protected PaymentRiskMocker payments;

    @Autowired
    protected PaymentRiskTestMapper store;

    /** 支付决策点。 */
    @Override
    protected String pointCode() {
        return PAYMENT;
    }

    /** 金额类累计变量绑定支付币种。 */
    @Override
    protected String currencyVariableCode() {
        return DecisionPointAuthoringDescriptor.paymentAttemptRisk().currencyVariableCode();
    }

    /** 支付决策点 Feature 变量的公共结构。 */
    protected static CreateVariableRequestBody featureVariable(String code, String sourceType, String valueType) {
        return featureVariable(code, sourceType, valueType, PAYMENT);
    }

    /** 以支付币种为币种坐标的金额字面量。 */
    protected static OperandRequestBody money(String amount) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", amount, null, "MONEY", null,
            "PAY_INPUT_CURRENCY");
    }

    /** 流程连线条件：支付节点处理结果等于给定决定。 */
    protected static String nodeDecision(DecisionValue decision) {
        return "{\"root\":{\"kind\":\"PREDICATE\",\"operands\":["
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":null,"
            + "\"numericUnit\":null,\"operandRole\":\"LEFT\",\"sourceType\":\"VARIABLE\",\"valueSequence\":1,"
            + "\"valueType\":\"STRING\",\"variableCode\":\"PAY_NODE_DECISION\"},"
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":\"" + decision.getCode()
            + "\",\"numericUnit\":null,\"operandRole\":\"RIGHT\",\"sourceType\":\"LITERAL\",\"valueSequence\":1,"
            + "\"valueType\":\"STRING\",\"variableCode\":null}],\"operator\":\"EQ\"},\"schemaVersion\":2}";
    }

    /** 流程连线条件：上一规则集节点有规则命中。 */
    protected static String nodeMatched() {
        return "{\"root\":{\"kind\":\"PREDICATE\",\"operands\":["
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":null,"
            + "\"numericUnit\":null,\"operandRole\":\"LEFT\",\"sourceType\":\"VARIABLE\",\"valueSequence\":1,"
            + "\"valueType\":\"BOOLEAN\",\"variableCode\":\"PAY_NODE_MATCHED\"},"
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":true,"
            + "\"numericUnit\":null,\"operandRole\":\"RIGHT\",\"sourceType\":\"LITERAL\",\"valueSequence\":1,"
            + "\"valueType\":\"BOOLEAN\",\"variableCode\":null}],\"operator\":\"EQ\"},\"schemaVersion\":2}";
    }

    /** 要求 3DS 的放行结束节点输出。 */
    protected static Map<String, String> threeDsRequired() {
        return Map.of(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT, AuthenticationRequirement.THREE_DS_REQUIRED.getCode());
    }

    /**
     * 标准两段流程：拦截规则集 → 节点拒绝走拒绝结束、节点审核走审核结束，否则 → 认证规则集 → 有命中走“放行（需要 3DS）”结束，否则放行结束
     *
     * @param blockRuleSet 拦截规则集版本
     * @param authRuleSet 认证要求规则集版本
     * @param reasonCode 拒绝结束的原因编码
     * @return 配置图
     */
    protected static GraphSpec twoStageGraph(long blockRuleSet, long authRuleSet, String reasonCode) {
        return new GraphSpec(List.of(
            new NodeSpec("start", FlowNodeType.START, "Start", 0, 0, null, null, null, null),
            new NodeSpec("block", FlowNodeType.RULE_SET, "Block", 120, 0, blockRuleSet, null, null, null),
            new NodeSpec("auth", FlowNodeType.RULE_SET, "Auth", 240, 0, authRuleSet, null, null, null),
            new NodeSpec("deny", FlowNodeType.END, "Deny", 240, 120, null, DecisionValue.DENY, reasonCode, "支付被风控拒绝"),
            new NodeSpec("review", FlowNodeType.END, "Review", 240, -120, null, DecisionValue.REVIEW, null, null),
            new NodeSpec("allow", FlowNodeType.END, "Allow", 360, 0, null, DecisionValue.ALLOW, null, null),
            new NodeSpec("allow_3ds", FlowNodeType.END, "Allow with 3DS", 360, 120, null, DecisionValue.ALLOW, null, null,
                threeDsRequired())),
            List.of(new EdgeSpec("enter", "start", "block", FlowEdgeType.UNCONDITIONAL, 0, "Enter", null),
                new EdgeSpec("to_deny", "block", "deny", FlowEdgeType.CONDITIONAL, 1, "Denied",
                    nodeDecision(DecisionValue.DENY)),
                new EdgeSpec("to_review", "block", "review", FlowEdgeType.CONDITIONAL, 2, "Reviewed",
                    nodeDecision(DecisionValue.REVIEW)),
                new EdgeSpec("to_auth", "block", "auth", FlowEdgeType.ELSE, 0, "Otherwise", null),
                new EdgeSpec("to_3ds", "auth", "allow_3ds", FlowEdgeType.CONDITIONAL, 1, "Authentication", nodeMatched()),
                new EdgeSpec("finish", "auth", "allow", FlowEdgeType.ELSE, 0, "Finish", null)));
    }
}
