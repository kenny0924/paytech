package com.paytech.platform.support.auto.ext.http.dataquery;

import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ACTION_BY_ATTEMPT;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ATTEMPT_BY_ID;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ATTEMPT_BY_INTENT;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.BILLING_BY_ORDER;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.INTENT_BY_ID;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.INTENT_BY_ORDER;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.ORDER_BY_ID;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.REFUND_BY_NO;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.REFUND_BY_ORDER;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.REFUND_NO;
import static com.paytech.platform.support.test.dataquery.JointQueryMocker.SESSION_BY_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.dataquery.JointQueryApi;
import com.paytech.platform.support.api.dataquery.request.JointQueryStepRequestBody;
import com.paytech.platform.support.api.dataquery.response.JointQueryAlreadyLoaded;
import com.paytech.platform.support.api.dataquery.response.JointQueryNode;
import com.paytech.platform.support.api.dataquery.response.JointQueryStepResponseBody;
import com.paytech.platform.support.dataquery.core.enums.JointQueryNodeStatus;
import com.paytech.platform.support.test.base.JointQueryApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;

/**
 * 联合查询已加载对象跳过（technique 8.7、需求 B5b / AC-13）：“按自身 ID 查”的模板（触发键类型 = 行主体键类型）
 * 跳过页面已加载的对象值，不生成节点，记入 alreadyLoaded；其他模板、入口跳与 templateCodes 指定的执行不跳过。
 * 不计入单步执行数见 {@link JointQueryStepGuardrailApiTest#test_loadedSubjectsNotCountedTowardStepLimit()}。
 *
 * @see JointQueryApi#executeJointQueryStep(String, String, String, JointQueryStepRequestBody)
 */
class JointQueryLoadedSubjectApiTest extends JointQueryApiTestBase {

    private Operator admin;

    /** 本类公共：平台管理员、链路键类型、一个 ACTIVE 只读数据源与退款链路模板。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        jointQuery.chainKeyTypes();
        jointQuery.chainTemplates(dataQuery.activeReaderSource("FIXTURE_JOINT_LOADED"));
    }

    /**
     * 退款单号入口按页面方式带已加载对象逐层展开：支付意图已由“按订单查意图”取得、支付尝试已由“按意图查尝试”取得，
     * 因而“按意图 ID 查”“按尝试 ID 查”不再执行、只记 alreadyLoaded；按订单查的退款 / 意图 / 计费等照常执行，链路完整。
     */
    @Test
    void test_selfIdTemplatesSkipLoadedSubjects() {
        List<JointQueryStepResponseBody> steps = runChainSkippingLoaded(admin, "REFUND_REQUEST_NO", REFUND_NO);

        List<String> codes = nodes(steps).stream().map(JointQueryNode::getTemplateCode).toList();
        List<JointQueryAlreadyLoaded> skipped = steps.stream().flatMap(step -> step.getAlreadyLoaded().stream())
            .toList();
        assertAll(() -> assertEquals(List.of(Set.of(REFUND_BY_NO),
                Set.of(REFUND_BY_ORDER, ORDER_BY_ID, INTENT_BY_ORDER, BILLING_BY_ORDER),
                Set.of(SESSION_BY_ID, ATTEMPT_BY_INTENT), Set.of(ACTION_BY_ATTEMPT), Set.of()),
                steps.stream().map(step -> step.getNodes().stream().map(JointQueryNode::getTemplateCode)
                    .collect(Collectors.toSet())).toList()),
            () -> assertEquals(8, codes.size()),
            () -> assertTrue(nodes(steps).stream().allMatch(node -> JointQueryNodeStatus.SUCCEEDED.getCode()
                .equals(node.getStatus()))),
            () -> assertEquals(List.of(INTENT_BY_ID + ":PAYMENT_INTENT_ID:[8001]",
                ATTEMPT_BY_ID + ":PAYMENT_ATTEMPT_ID:[9001, 9002]"), skipped.stream().map(item ->
                item.getTemplateCode() + ":" + item.getKeyType() + ":" + item.getValues()).toList()),
            () -> assertEquals(List.of("9001", "9002"), node(nodes(steps), ACTION_BY_ATTEMPT).getTriggerValues()),
            () -> assertTrue(skipped.stream().allMatch(item -> item.getTemplateName() != null)));
    }

    /**
     * 部分已加载：“按订单 ID 查订单”只用未加载的值执行（已加载值按值类型规范化后比较），同一触发键的“按订单查意图”
     * 不是按自身 ID 查，照常用全部值执行。
     */
    @Test
    void test_partiallyLoadedExecutesRemainingValues() {
        JointQueryStepRequestBody body = stepBody(1, "ORDER_ID", "5001", "5002");
        body.setLoadedSubjects(List.of(loadedSubject("ORDER_ID", "05001"), loadedSubject("NO_SUCH_KEY", "x")));

        var step = stepOk(admin, body);

        assertAll(() -> assertEquals(List.of("5002"), node(step.getNodes(), ORDER_BY_ID).getTriggerValues()),
            () -> assertEquals(1, node(step.getNodes(), ORDER_BY_ID).getRows().size()),
            () -> assertEquals(List.of("5001", "5002"), node(step.getNodes(), INTENT_BY_ORDER).getTriggerValues()),
            () -> assertEquals(List.of("5001", "5002"), node(step.getNodes(), REFUND_BY_ORDER).getTriggerValues()),
            () -> assertEquals(1, step.getAlreadyLoaded().size()),
            () -> assertEquals(ORDER_BY_ID, step.getAlreadyLoaded().getFirst().getTemplateCode()),
            () -> assertEquals(List.of("5001"), step.getAlreadyLoaded().getFirst().getValues()));
    }

    /** 入口跳（depth 0）与 templateCodes 指定的执行（页面“按需查看”）即使对象已加载也照常执行。 */
    @Test
    void test_entryStepAndTemplateCodesNeverSkipped() {
        JointQueryStepRequestBody entry = stepBody(0, "PAYMENT_ATTEMPT_ID", "9001");
        entry.setLoadedSubjects(List.of(loadedSubject("PAYMENT_ATTEMPT_ID", "9001")));
        JointQueryStepRequestBody onDemand = stepBody(1, "ORDER_ID", "5001");
        onDemand.setTemplateCodes(List.of(ORDER_BY_ID));
        onDemand.setLoadedSubjects(List.of(loadedSubject("ORDER_ID", "5001")));

        var entryStep = stepOk(admin, entry);
        var onDemandStep = stepOk(admin, onDemand);

        assertAll(() -> assertEquals(List.of("9001"), node(entryStep.getNodes(), ATTEMPT_BY_ID).getTriggerValues()),
            () -> assertTrue(entryStep.getAlreadyLoaded().isEmpty()),
            () -> assertEquals(List.of("5001"), node(onDemandStep.getNodes(), ORDER_BY_ID).getTriggerValues()),
            () -> assertEquals(1, onDemandStep.getNodes().size()),
            () -> assertTrue(onDemandStep.getAlreadyLoaded().isEmpty()));
    }

    /** 从下游支付尝试进入时上游对象尚未加载，“按自身 ID 查”的意图、订单与收银台会话模板照常执行各一次。 */
    @Test
    void test_upstreamEntryStillRunsSelfIdTemplates() {
        List<JointQueryNode> nodes = nodes(runChainSkippingLoaded(admin, "PAYMENT_ATTEMPT_ID", "9001"));

        assertAll(() -> assertEquals(List.of("8001"), node(nodes, INTENT_BY_ID).getTriggerValues()),
            () -> assertEquals(List.of("5001"), node(nodes, ORDER_BY_ID).getTriggerValues()),
            () -> assertEquals(List.of("6001"), node(nodes, SESSION_BY_ID).getTriggerValues()),
            () -> assertEquals(List.of("9001"), node(nodes, ATTEMPT_BY_ID).getTriggerValues()));
    }

    /** 已加载对象的值不能按键类型规范化时返回 422，field 定位到值；超过 30 个键类型或单类 200 个值返回 422。 */
    @Test
    void test_invalidLoadedSubjects() {
        JointQueryStepRequestBody badValue = stepBody(1, "ORDER_ID", "5001");
        badValue.setLoadedSubjects(List.of(loadedSubject("ORDER_ID", "5001", "abc")));
        JointQueryStepRequestBody tooManyTypes = stepBody(1, "ORDER_ID", "5001");
        tooManyTypes.setLoadedSubjects(IntStream.range(0, 31).mapToObj(index -> loadedSubject("KEY_" + index, "1"))
            .toList());
        JointQueryStepRequestBody tooManyValues = stepBody(1, "ORDER_ID", "5001");
        tooManyValues.setLoadedSubjects(List.of(loadedSubject("ORDER_ID",
            IntStream.range(0, 201).mapToObj(Integer::toString).toArray(String[]::new))));

        step(admin, badValue).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("loadedSubjects[0].values[1]");
        step(admin, tooManyTypes).assertProblem(422, CommonErrorCode.VALIDATION_ERROR).assertFieldError("loadedSubjects");
        step(admin, tooManyValues).assertProblem(422, CommonErrorCode.VALIDATION_ERROR)
            .assertFieldError("loadedSubjects[0].values");
    }
}
