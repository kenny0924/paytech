package com.paytech.risk.test.base;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.risk.api.decision.DecisionRuleSetManagementModels.OperandRequestBody;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.evaluation.request.CheckoutPrepareRiskInput;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.test.builder.decision.FlowBuilder.EdgeSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.GraphSpec;
import com.paytech.risk.test.builder.decision.FlowBuilder.NodeSpec;
import com.paytech.risk.test.mapper.evaluation.CheckoutRiskTestMapper;
import com.paytech.risk.test.mocker.evaluation.CheckoutRiskMocker;

/**
 * 结账风控接口测试的公共入口辅助
 * <li>规则、流程与执行发布都在结账决策点经真实配置入口准备，编码统一以 {@link CheckoutRiskTestMapper#TEST_CODE_PREFIX} 开头，便于整体回收。</li>
 * <li>不含 @Test；全部数据由 {@link CheckoutRiskMocker} 在全局 AfterEach 回收。</li>
 *
 * @author Kenny
 */
public abstract class AbstractCheckoutRiskApiTest extends AbstractRuntimeRiskApiTest {
    /** 结账决策点。 */
    protected static final String CHECKOUT = CheckoutPrepareRiskInput.POINT_CODE;

    /** 拒绝结束节点的文案。 */
    protected static final String DENY_MESSAGE = "结账被风控拒绝";

    @Autowired
    protected CheckoutRiskMocker checkouts;

    @Autowired
    protected CheckoutRiskTestMapper store;

    /** 结账决策点。 */
    @Override
    protected String pointCode() {
        return CHECKOUT;
    }

    /** 金额类累计变量绑定订单币种。 */
    @Override
    protected String currencyVariableCode() {
        return DecisionPointAuthoringDescriptor.checkoutPrepare().currencyVariableCode();
    }

    /** 结账测试配置的编码：带统一前缀与随机后缀。 */
    protected static String checkoutCode(String name) {
        return newCode(CheckoutRiskTestMapper.TEST_CODE_PREFIX + "_" + name);
    }

    /** 创建结账决策点的普通数值常量变量，返回变量编码。 */
    protected String createScalarConstant(String name, String value) {
        String code = checkoutCode(name);
        CreateVariableRequestBody body = new CreateVariableRequestBody();
        body.setVariableCode(code);
        body.setVariableName(code);
        body.setSourceType(VariableSource.CONSTANT.getCode());
        body.setValueType(DecisionValueType.NUMBER.getCode());
        body.setPointCode(CHECKOUT);
        body.setUsages(List.of(VariableUsage.RULE_CONDITION.getCode(), VariableUsage.FLOW_CONDITION.getCode()));
        body.setFormatCode(VariableFormat.NONE.getCode());
        body.setNumericUnit(NumericUnit.SCALAR.getCode());
        body.setCurrentValue(value);
        body.setStatus(VariableStatus.ACTIVE.getCode());
        postOk("/decision-variables/v1/variables", body);
        return code;
    }

    /** 以订单币种为币种坐标的金额字面量。 */
    protected static OperandRequestBody money(String amount) {
        return new OperandRequestBody("RIGHT", 1, "LITERAL", null, "NUMBER", amount, null, "MONEY", null,
            "INPUT_CURRENCY");
    }

    /** 流程连线条件：结账节点处理结果等于给定决定。 */
    protected static String nodeDecision(DecisionValue decision) {
        return "{\"root\":{\"kind\":\"PREDICATE\",\"operands\":["
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":null,"
            + "\"numericUnit\":null,\"operandRole\":\"LEFT\",\"sourceType\":\"VARIABLE\",\"valueSequence\":1,"
            + "\"valueType\":\"STRING\",\"variableCode\":\"NODE_DECISION\"},"
            + "{\"currencyCode\":null,\"currencyVariableCode\":null,\"fieldCode\":null,\"literalValue\":\"" + decision.getCode()
            + "\",\"numericUnit\":null,\"operandRole\":\"RIGHT\",\"sourceType\":\"LITERAL\",\"valueSequence\":1,"
            + "\"valueType\":\"STRING\",\"variableCode\":null}],\"operator\":\"EQ\"},\"schemaVersion\":2}";
    }

    /**
     * 标准结账流程：开始 → 拦截规则集 → 节点拒绝走拒绝结束、节点审核走审核结束，否则放行结束
     *
     * @param blockRuleSet 拦截规则集版本
     * @param reasonCode 拒绝结束的原因编码
     * @return 配置图
     */
    protected static GraphSpec blockGraph(long blockRuleSet, String reasonCode) {
        return new GraphSpec(List.of(
            new NodeSpec("start", FlowNodeType.START, "Start", 0, 0, null, null, null, null),
            new NodeSpec("block", FlowNodeType.RULE_SET, "Block", 120, 0, blockRuleSet, null, null, null),
            new NodeSpec("deny", FlowNodeType.END, "Deny", 240, 120, null, DecisionValue.DENY, reasonCode, DENY_MESSAGE),
            new NodeSpec("review", FlowNodeType.END, "Review", 240, -120, null, DecisionValue.REVIEW, null, null),
            new NodeSpec("allow", FlowNodeType.END, "Allow", 240, 0, null, DecisionValue.ALLOW, null, null)),
            List.of(new EdgeSpec("enter", "start", "block", FlowEdgeType.UNCONDITIONAL, 0, "Enter", null),
                new EdgeSpec("to_deny", "block", "deny", FlowEdgeType.CONDITIONAL, 1, "Denied",
                    nodeDecision(DecisionValue.DENY)),
                new EdgeSpec("to_review", "block", "review", FlowEdgeType.CONDITIONAL, 2, "Reviewed",
                    nodeDecision(DecisionValue.REVIEW)),
                new EdgeSpec("finish", "block", "allow", FlowEdgeType.ELSE, 0, "Otherwise", null)));
    }
}
