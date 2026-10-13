package com.paytech.risk.auto.ext.http.evaluation;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.test.builder.evaluation.ManualPayoutEligibilityRequestBuilder.*;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.evaluation.ManualPayoutEligibilityApi;
import com.paytech.risk.api.evaluation.request.ManualPayoutEligibilityRequestBody;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.builder.evaluation.ManualPayoutEligibilityRequestBuilder;
import com.paytech.risk.test.checker.evaluation.ManualPayoutEligibilityResponseChecker;
import com.paytech.risk.test.checker.evaluation.SettlementRiskAssessmentChecker;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * RCO-I09 Manual Payout 资格评估
 * <li>正常流程：四个检查点、两类角色及金额/ID 边界的合法请求一律 Fail-Closed（503），且不落评估记录。</li>
 * <li>参数校验：每个字段的缺失、格式、长度与范围由入口 Bean Validation 拒绝（422）并定位字段。</li>
 * <li>业务规则：检查点/来源矩阵、创建类版本、角色、signed BIGINT 与 inputHash 绑定在评估前拒绝（400）； sourceFactType
 * 与检查点一一对应，其改动先命中来源矩阵，不单列摘要绑定用例。</li>
 * <li>报文边界：无法解析的报文返回 400，非 JSON 媒体类型返回 415。</li>
 *
 * @see ManualPayoutEligibilityApi#evaluate(ManualPayoutEligibilityRequestBody)
 */
class ManualPayoutEligibilityApiAutoTest extends MockTestBase {
    private static final String PATH = "/manual-payout-eligibility/v1/evaluations";

    /** 超出 signed BIGINT 上限 1，但仍满足 19 位数字格式。 */
    private static final String BIGINT_OVERFLOW = "9223372036854775808";

    private static final String BIGINT_MAX = "9223372036854775807";

    @Autowired
    private SettlementRiskAssessmentChecker assessmentChecker;

    /** 合法请求在评估链接入前一律 Fail-Closed，不产生任何决定或评估记录。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("validRequests")
    void test_normalFlow(String scenario, Supplier<ManualPayoutEligibilityRequestBuilder> request) {
        ManualPayoutEligibilityRequestBody body = request.get().build();

        invoke(body).assertFailClosed();

        assessmentChecker.assertNotRecorded(body.getInputHash());
    }

    /** 字段格式由入口 Bean Validation 拒绝，并定位到具体字段。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidFields")
    void test_invalidParameter(String field, String scenario, Supplier<ManualPayoutEligibilityRequestBuilder> request) {
        invoke(request.get().build()).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);
    }

    /** 格式合法但违反检查点合同的输入在评估前拒绝，不会落入 Fail-Closed。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contractViolations")
    void test_businessRuleViolation(String scenario, Supplier<ManualPayoutEligibilityRequestBuilder> request) {
        ManualPayoutEligibilityRequestBody body = request.get().build();

        invoke(body).assertProblem(400, INVALID_REQUEST.getCode());

        assessmentChecker.assertNotRecorded(body.getInputHash());
    }

    /** 无法反序列化为请求合同的报文统一返回 400。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void test_malformedBody(String scenario, Function<ObjectMapper, String> rawBody) {
        invoke(TestRequestHeaders.internal(nextRequestId()), rawBody.apply(objectMapper)).assertProblem(400,
            INVALID_REQUEST.getCode());
    }

    /** 非 JSON 请求在进入合同前以 415 拒绝。 */
    @Test
    void test_unsupportedMediaType() {
        HttpHeaders headers = TestRequestHeaders.internal(nextRequestId());
        headers.setContentType(MediaType.TEXT_PLAIN);

        invoke(headers, json(clientCreation())).assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());
    }

    /** 四个检查点 × 两类角色、命令键不参与摘要，以及金额与 ID 的合法边界。 */
    static Stream<Arguments> validRequests() {
        return Stream.of(
            Arguments.of("Client PAYOUT_CREATION", request(ManualPayoutEligibilityRequestBuilder::clientCreation)),
            Arguments.of("Local Partner PAYOUT_FINAL_APPROVAL 非创建类版本 8",
                request(ManualPayoutEligibilityRequestBuilder::localPartnerFinalApproval)),
            Arguments.of("Client PAYOUT_EXECUTION_CREATION",
                request(ManualPayoutEligibilityRequestBuilder::clientExecutionCreation)),
            Arguments.of("Local Partner PAYOUT_EXTERNAL_SUBMISSION 非创建类版本 3",
                request(ManualPayoutEligibilityRequestBuilder::localPartnerExternalSubmission)),
            Arguments.of("commandKey 不参与 inputHash", request(() -> clientCreation().commandKey("cmd-other-key"))),
            Arguments.of("commandKey 128 位上限", request(() -> clientCreation().commandKey("k".repeat(128)))),
            Arguments.of("小数金额 102.5",
                request(() -> clientCreation().grossAmount("102.5")
                    .inputHash("62100afcd4e7183a5bad644868309ce4cac14cf837107c0df46631a1e5f6deb9"))),
            Arguments.of("最小金额 0.00000001",
                request(() -> clientCreation().grossAmount("0.00000001")
                    .inputHash("daa349df27586163c3c847d267e3ce7b5551fb172a5b44f0bf4eb069589e31b7"))),
            Arguments.of("最大金额 16 位整数 8 位小数",
                request(() -> clientCreation().grossAmount("9999999999999999.99999999")
                    .inputHash("d101ca75ae35df4dcab640468d2f9d673f19e4a029b9986012bc002bd35ec483"))),
            Arguments.of("三个 ID 均为 signed BIGINT 上限",
                request(() -> clientCreation().roleId(BIGINT_MAX).operatingEntityId(BIGINT_MAX).sourceFactId(BIGINT_MAX)
                    .inputHash("d0970b06f9f4efbc0ac2220abf90cab2c8f08aedcdc9ccb8e0e101a7374273e2"))));
    }

    /** 逐字段的缺失、格式、长度与范围负例。 */
    static Stream<Arguments> invalidFields() {
        return Stream.of(invalid("commandKey", "缺失", () -> clientCreation().commandKey(null)),
            invalid("commandKey", "空白", () -> clientCreation().commandKey(" ")),
            invalid("commandKey", "超过 128 位", () -> clientCreation().commandKey("k".repeat(129))),
            invalid("checkpointType", "缺失", () -> clientCreation().checkpointType(null)),
            invalid("checkpointType", "小写", () -> clientCreation().checkpointType("payout_creation")),
            invalid("checkpointType", "超过 40 位", () -> clientCreation().checkpointType("P".repeat(41))),
            invalid("roleType", "缺失", () -> clientCreation().roleType(null)),
            invalid("roleType", "小写", () -> clientCreation().roleType("client")),
            invalid("roleType", "超过 32 位", () -> clientCreation().roleType("R".repeat(33))),
            invalid("roleId", "缺失", () -> clientCreation().roleId(null)),
            invalid("roleId", "零", () -> clientCreation().roleId("0")),
            invalid("roleId", "前导零", () -> clientCreation().roleId("040001")),
            invalid("roleId", "非数字", () -> clientCreation().roleId("4000A")),
            invalid("roleId", "20 位数字", () -> clientCreation().roleId("1".repeat(20))),
            invalid("operatingEntityId", "缺失", () -> clientCreation().operatingEntityId(null)),
            invalid("operatingEntityId", "零", () -> clientCreation().operatingEntityId("0")),
            invalid("operatingEntityId", "20 位数字", () -> clientCreation().operatingEntityId("1".repeat(20))),
            invalid("grossAmount", "缺失", () -> clientCreation().grossAmount(null)),
            invalid("grossAmount", "零", () -> clientCreation().grossAmount("0")),
            invalid("grossAmount", "负数", () -> clientCreation().grossAmount("-1")),
            invalid("grossAmount", "小数末尾为零", () -> clientCreation().grossAmount("1.50")),
            invalid("grossAmount", "缺整数部分", () -> clientCreation().grossAmount(".5")),
            invalid("grossAmount", "科学计数法", () -> clientCreation().grossAmount("1E3")),
            invalid("grossAmount", "整数 17 位", () -> clientCreation().grossAmount("1" + "0".repeat(16))),
            invalid("grossAmount", "小数 9 位", () -> clientCreation().grossAmount("1.000000001")),
            invalid("currency", "缺失", () -> clientCreation().currency(null)),
            invalid("currency", "小写", () -> clientCreation().currency("aed")),
            invalid("currency", "2 位", () -> clientCreation().currency("AE")),
            invalid("bankCountry", "缺失", () -> clientCreation().bankCountry(null)),
            invalid("bankCountry", "小写", () -> clientCreation().bankCountry("ae")),
            invalid("bankCountry", "3 位", () -> clientCreation().bankCountry("ARE")),
            invalid("sourceFactType", "缺失", () -> clientCreation().sourceFactType(null)),
            invalid("sourceFactType", "小写", () -> clientCreation().sourceFactType("payout")),
            invalid("sourceFactType", "超过 32 位", () -> clientCreation().sourceFactType("S".repeat(33))),
            invalid("sourceFactId", "缺失", () -> clientCreation().sourceFactId(null)),
            invalid("sourceFactId", "零", () -> clientCreation().sourceFactId("0")),
            invalid("sourceFactId", "20 位数字", () -> clientCreation().sourceFactId("1".repeat(20))),
            invalid("sourceFactVersion", "缺失", () -> clientCreation().sourceFactVersion(null)),
            invalid("sourceFactVersion", "零", () -> clientCreation().sourceFactVersion(0)),
            invalid("sourceFactVersion", "负数", () -> clientCreation().sourceFactVersion(-1)),
            invalid("inputHash", "缺失", () -> clientCreation().inputHash(null)),
            invalid("inputHash", "大写", () -> clientCreation().inputHash("A".repeat(64))),
            invalid("inputHash", "63 位", () -> clientCreation().inputHash("a".repeat(63))),
            invalid("inputHash", "非十六进制", () -> clientCreation().inputHash("g".repeat(64))));
    }

    /**
     * 检查点/来源矩阵、创建类版本、角色、ID 范围，以及每个标准输入字段都受 inputHash 约束
     * <li>规则负例携带按自身输入独立计算的正确摘要，确保只违反目标规则，不被后续摘要校验掩盖。</li>
     * <li>摘要绑定负例只改一个字段且保留原摘要，其余规则均满足。</li>
     */
    static Stream<Arguments> contractViolations() {
        return Stream.of(
            Arguments.of("PAYOUT_CREATION 使用 PAYOUT_EXECUTION 来源",
                request(() -> clientCreation().sourceFactType("PAYOUT_EXECUTION")
                    .inputHash("cb867712ec615073dfa3718feda36d6e1b724969b77215207ff7cb53dd1435c9"))),
            Arguments.of("PAYOUT_EXECUTION_CREATION 使用 PAYOUT 来源",
                request(() -> clientExecutionCreation().sourceFactType("PAYOUT")
                    .inputHash("2b5f50979b056268fe3f029b2e9a5eec5ae083e6598e771653a540898b0586df"))),
            Arguments.of("未知来源事实类型",
                request(() -> clientCreation().sourceFactType("SETTLEMENT")
                    .inputHash("a9d77aff7635b28de6ad587a2045322f9cf451696e0803f5c6ca841841493bf2"))),
            Arguments.of("未知检查点",
                request(() -> clientCreation().checkpointType("PAYOUT_SETTLEMENT")
                    .inputHash("72a182f10481978900ba3123c6366d2be6c4b6003494bd9c7ebc458edca2c3ae"))),
            Arguments.of("PAYOUT_CREATION 版本不为 1",
                request(() -> clientCreation().sourceFactVersion(2)
                    .inputHash("6ba83866b82db70c4885aa59a3fb5be5aa81fc3522517b8b34243ea9b6f8d6c8"))),
            Arguments.of("PAYOUT_EXECUTION_CREATION 版本不为 1",
                request(() -> clientExecutionCreation().sourceFactVersion(2)
                    .inputHash("557ae6a817f772f5d6b6603e5b20cbdca3f2e7631170928cbbc5c01225318f6a"))),
            Arguments.of("未知出款角色",
                request(() -> clientCreation().roleType("MERCHANT")
                    .inputHash("88c0f95d7ac6781b97c695d2e480d35e3737c35998b360685be72341609092e3"))),
            Arguments.of("roleId 超出 signed BIGINT",
                request(() -> clientCreation().roleId(BIGINT_OVERFLOW)
                    .inputHash("b284aaf1f5751fbe0d283cc1286092f25a5c6db205353f07ee1daabfe7bae4f5"))),
            Arguments.of("operatingEntityId 超出 signed BIGINT",
                request(() -> clientCreation().operatingEntityId(BIGINT_OVERFLOW)
                    .inputHash("00b0a8b56913568c01193dc560cd0ee955b20f5b80ad0a93baf9b941d3c4c762"))),
            Arguments.of("sourceFactId 超出 signed BIGINT",
                request(() -> clientCreation().sourceFactId(BIGINT_OVERFLOW)
                    .inputHash("074f887cc460dbe52b62c2fcd6a10451eff3c3c357c9482935c7d7640edfed43"))),
            Arguments.of("inputHash 与标准输入不一致", request(() -> clientCreation().inputHash("a".repeat(64)))),
            Arguments.of("改 checkpointType 未更新摘要",
                request(() -> clientCreation().checkpointType("PAYOUT_FINAL_APPROVAL"))),
            Arguments.of("改 roleType 未更新摘要", request(() -> clientCreation().roleType("LOCAL_PARTNER"))),
            Arguments.of("改 roleId 未更新摘要", request(() -> clientCreation().roleId("40002"))),
            Arguments.of("改 operatingEntityId 未更新摘要", request(() -> clientCreation().operatingEntityId("50002"))),
            Arguments.of("改 grossAmount 未更新摘要", request(() -> clientCreation().grossAmount("103"))),
            Arguments.of("改 currency 未更新摘要", request(() -> clientCreation().currency("USD"))),
            Arguments.of("改 bankCountry 未更新摘要", request(() -> clientCreation().bankCountry("SA"))),
            Arguments.of("改 sourceFactId 未更新摘要", request(() -> clientCreation().sourceFactId("92002"))),
            Arguments.of("改 sourceFactVersion 未更新摘要", request(() -> localPartnerFinalApproval().sourceFactVersion(9))));
    }

    /** 无法绑定到请求合同的原始报文。 */
    static Stream<Arguments> malformedBodies() {
        return Stream.of(Arguments.of("非法 JSON", raw(mapper -> "{\"commandKey\":")),
            Arguments.of("空报文", raw(mapper -> "")), Arguments.of("顶层为数组", raw(mapper -> "[]")),
            Arguments.of("未知字段", raw(mapper -> withField(mapper, "decision", "ALLOW"))),
            Arguments.of("sourceFactVersion 超出 int",
                raw(mapper -> withRawField(mapper, "sourceFactVersion", "2147483648"))),
            Arguments.of("sourceFactVersion 为字符串", raw(mapper -> withField(mapper, "sourceFactVersion", "one"))));
    }

    /** 让参数化用例中的 Lambda 具备明确的目标类型。 */
    private static Supplier<ManualPayoutEligibilityRequestBuilder>
        request(Supplier<ManualPayoutEligibilityRequestBuilder> request) {
        return request;
    }

    private static Function<ObjectMapper, String> raw(Function<ObjectMapper, String> body) {
        return body;
    }

    private static Arguments invalid(String field, String scenario,
        Supplier<ManualPayoutEligibilityRequestBuilder> request) {
        return Arguments.of(field, scenario, request);
    }

    /** 合法 Client 创建请求序列化后设置一个字符串字段，可追加未知字段或替换为错误类型。 */
    private static String withField(ObjectMapper mapper, String name, String value) {
        return jsonNode(mapper).put(name, value).toString();
    }

    /** 合法 Client 创建请求序列化后以未加引号的原值覆盖一个字段，构造超出 Java 类型的数字。 */
    private static String withRawField(ObjectMapper mapper, String name, String rawValue) {
        ObjectNode node = jsonNode(mapper);
        node.remove(name);
        String json = node.toString();
        return json.substring(0, json.length() - 1) + ",\"" + name + "\":" + rawValue + "}";
    }

    private static ObjectNode jsonNode(ObjectMapper mapper) {
        return mapper.valueToTree(clientCreation().build());
    }

    private String json(ManualPayoutEligibilityRequestBuilder builder) {
        return objectMapper.writeValueAsString(builder.build());
    }

    /** 以私网内部调用发送真实 HTTP 请求，每次使用独立 Request ID。 */
    private ManualPayoutEligibilityResponseChecker invoke(ManualPayoutEligibilityRequestBody body) {
        return invoke(TestRequestHeaders.internal(nextRequestId()), body);
    }

    private ManualPayoutEligibilityResponseChecker invoke(HttpHeaders headers, Object body) {
        String requestId = headers.getFirst(ApiHeaderNames.REQUEST_ID);
        return new ManualPayoutEligibilityResponseChecker(http.post(PATH, headers, body), objectMapper, requestId);
    }

    private static String nextRequestId() {
        return "risk-payout-eligibility-" + UUID.randomUUID();
    }
}
