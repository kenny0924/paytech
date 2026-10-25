package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static com.paytech.risk.decision.api.enums.RiskVariableErrorCode.*;
import static com.paytech.risk.decision.domain.enums.DecisionValueType.*;
import static com.paytech.risk.decision.domain.enums.NumericUnit.SCALAR;
import static com.paytech.risk.decision.domain.enums.VariableFormat.*;
import static com.paytech.risk.test.builder.decision.VariableRequestBuilder.member;
import static com.paytech.risk.test.builder.decision.VariableRequestBuilder.members;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.decision.api.RiskVariableApi;
import com.paytech.risk.decision.api.RiskVariableModels.MemberRequest;
import com.paytech.risk.decision.api.request.ReplaceMembersRequestBody;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.test.base.AbstractVariableApiTest;
import com.paytech.risk.test.builder.decision.VariableBuilder;
import com.paytech.risk.test.checker.decision.VariableMembersResponseChecker;
import com.paytech.risk.test.mocker.decision.VariableMocker.MockedVariable;

import tools.jackson.databind.node.ObjectNode;

/**
 * 集合成员的分页读取与整批替换
 * <li>读取：缺省第 1 页每页 50 按顺序号返回；后续页须携带首次根版本，版本变化返回 409；关键字字面包含规范值或显示名称；各成员类型按 JSON 类型输出。</li>
 * <li>替换：按集合类型规范化（数字补齐八位小数、BCP47 规范大小写、字符串保留原文），显示名称去首尾空白，顺序号重排；根版本加一；空数组清空；上限 1000 项、64 KiB。</li>
 * <li>业务规则：非集合（409）、墓碑/不存在（404）、系统集合（403）、版本冲突（409）、规范值重复或成员值非法（400）、资源锁被占用（409），整批不写入。</li>
 * <li>参数：分页/版本/关键字/Query/GET Body、Bean Validation（422）、严格 JSON（400）、请求体上限（413）与媒体类型（415）。</li>
 *
 * @see RiskVariableApi#listMembers(String, String, String, String, Integer, Integer, String)
 * @see RiskVariableApi#replaceMembers(String, String, String, ReplaceMembersRequestBody)
 */
class VariableMembersApiAutoTest extends AbstractVariableApiTest {

    /** 缺省分页返回全部成员，按顺序号排列，根版本以字符串返回。 */
    @Test
    void test_normalFlow_list() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().version(6).member("Zed", "last")
            .member("A ", null).member("001", "zero one"));
        Instant from = now();

        list(collection, "").assertPage(collection.code(), 6, 1, 50, 3, collection.spec().members(), 1, 2, 3)
            .assertValuesAsOfBetween(from);
    }

    /** 各成员类型按集合类型的 JSON 形态输出。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("typedCollections")
    void test_normalFlow_listTypedValues(String scenario, Supplier<VariableBuilder> spec) {
        MockedVariable collection = variables.mock(spec.get());

        list(collection, "").assertPage(collection.code(), 1, 1, 50, 2, collection.spec().members(), 1, 2);
    }

    /** 后续页携带首次根版本按顺序号切分，越界页为空但保留总数。 */
    @Test
    void test_normalFlow_listPaging() {
        VariableBuilder spec = VariableBuilder.textCollection().version(3);
        IntStream.rangeClosed(1, 5).forEach(index -> spec.member("M" + index, null));
        MockedVariable collection = variables.mock(spec);
        List<VariableBuilder.MemberSpec> all = collection.spec().members();

        list(collection, "size=2").assertPage(collection.code(), 3, 1, 2, 5, all, 1, 2);
        list(collection, "size=2&page=2&expectedVersion=3").assertPage(collection.code(), 3, 2, 2, 5, all, 3, 4);
        list(collection, "size=2&page=3&expectedVersion=3").assertPage(collection.code(), 3, 3, 2, 5, all, 5);
        list(collection, "size=2&page=4&expectedVersion=3").assertPage(collection.code(), 3, 4, 2, 5, all);
        list(collection, "page=1&expectedVersion=3&size=100").assertPage(collection.code(), 3, 1, 100, 5, all, 1, 2, 3,
            4, 5);
        list(collection, "page=10000&expectedVersion=3").assertPage(collection.code(), 3, 10000, 50, 5, all);
    }

    /** 关键字按字面包含匹配规范值或显示名称，通配符不生效，总数随筛选变化。 */
    @Test
    void test_normalFlow_listKeyword() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().member("A_1", null)
            .member("AB1", null).member("X", "名称_1").member("Y", "other"));
        List<VariableBuilder.MemberSpec> all = collection.spec().members();

        list(collection, "keyword=_1").assertPage(collection.code(), 1, 1, 50, 2, all, 1, 3);
        list(collection, "keyword=名称").assertPage(collection.code(), 1, 1, 50, 1, all, 3);
        list(collection, "keyword=%").assertPage(collection.code(), 1, 1, 50, 0, all);
    }

    /** 空集合返回空页。 */
    @Test
    void test_noDataFound_emptyCollection() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection());

        list(collection, "").assertPage(collection.code(), 1, 1, 50, 0, List.of());
    }

    /** 编码不存在或集合已删除。 */
    @Test
    void test_noDataFound() {
        MockedVariable deleted = variables.mock(VariableBuilder.textCollection().status(VariableStatus.DELETED)
            .member("A", null));

        list(deleted, "").assertNotFound();
        invokeList(variablePath(variables.token() + "_ABSENT") + "/members").assertNotFound();
    }

    /** 非集合变量没有成员。 */
    @Test
    void test_businessRuleViolation_listNotCollection() {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v"));

        list(constant, "").assertProblem(409, VARIABLE_TYPE_MISMATCH.getCode());
    }

    /** 携带的首次根版本已变化时拒绝翻页，防止混合两批成员。 */
    @Test
    void test_businessRuleViolation_listVersionChanged() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().version(4).member("A", null));

        list(collection, "expectedVersion=3").assertProblem(409, VARIABLE_VERSION_CONFLICT.getCode());
        list(collection, "page=2&expectedVersion=5").assertProblem(409, VARIABLE_VERSION_CONFLICT.getCode());
    }

    /** 第 2 页起必须携带首次根版本。 */
    @Test
    void test_businessRuleViolation_listLaterPageWithoutVersion() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().member("A", null));

        list(collection, "page=2").assertProblem(400, INVALID_VARIABLE_DEFINITION.getCode());
    }

    /** 成员规范键损坏时失败关闭。 */
    @Test
    void test_businessRuleViolation_listCorruptedMember() {
        MockedVariable collection = variables.mock(VariableBuilder.collection(NUMBER, NONE).numericUnit(SCALAR)
            .member("1.00000000", null));
        variables.corruptMemberKeys(collection);

        list(collection, "").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 查询串非法时在读取前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidListQueries")
    void test_invalidParameter_list(String scenario, String query) {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().member("A", null));

        list(collection, query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 携带 Body 的 GET 请求与非法路径编码。 */
    @Test
    void test_invalidParameter_listRequest() {
        MockedVariable collection = variables.mock(VariableBuilder.textCollection().member("A", null));
        HttpHeaders headers = headers();

        new VariableMembersResponseChecker(http.exchange(HttpMethod.GET, membersPath(collection.code()), headers, "{}"),
            objectMapper, headers.getFirst(ApiHeaderNames.REQUEST_ID)).assertProblem(400, INVALID_REQUEST.getCode());
        invokeList(membersPath("lower_code")).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 整批替换：按集合类型规范化成员值，显示名称去首尾空白，顺序号按提交顺序重排，根版本加一。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("replacements")
    void test_normalFlow_replace(String scenario, Supplier<VariableBuilder> collection, List<MemberRequest> request,
        Supplier<VariableBuilder> expected) {
        MockedVariable target = variables.mock(collection.get().member(sample(collection.get()), "old"));
        Instant from = now();

        VariableMembersResponseChecker response = replace(target.code(), new ReplaceMembersRequestBody("1", request));

        assertReplaced(target, expected.get().members(), from, response);
    }

    /** 空数组清空集合；停用集合同样可维护成员。 */
    @Test
    void test_normalFlow_replaceClear() {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().status(VariableStatus.DISABLED).version(2)
            .member("A", null).member("B", "bee"));
        Instant from = now();

        VariableMembersResponseChecker response = replace(target.code(), members("2"));

        assertReplaced(target, List.of(), from, response);
    }

    /** 成员上限 1000 项且请求体恰好 64 KiB 时仍可替换。 */
    @Test
    void test_edgeCase_replaceAtLimits() {
        MockedVariable target = variables.mock(VariableBuilder.textCollection());
        VariableBuilder expected = VariableBuilder.textCollection();
        List<MemberRequest> request = new ArrayList<>();
        IntStream.range(0, 1000).forEach(index -> {
            request.add(member("V" + index, null));
            expected.member("V" + index, null);
        });
        Instant from = now();

        VariableMembersResponseChecker response = replace(headers(), target.code(),
            padded(new ReplaceMembersRequestBody("1", request), 65536));

        assertReplaced(target, expected.members(), from, response);
    }

    /** 规范值重复或任一成员非法时整批失败，根版本与原成员不变。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMembers")
    void test_businessRuleViolation_replaceInvalidMembers(String scenario, Supplier<VariableBuilder> collection,
        List<MemberRequest> request) {
        MockedVariable target = variables.mock(collection.get().member(sample(collection.get()), "kept"));

        replace(target.code(), new ReplaceMembersRequestBody("1", request)).assertProblem(400,
            INVALID_VARIABLE_DEFINITION.getCode());

        variableData.assertUnchanged(target);
    }

    /** 期望根版本不符时拒绝，原成员不变。 */
    @Test
    void test_businessRuleViolation_replaceVersionConflict() {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().version(2).member("A", null));

        replace(target.code(), members("1", member("B", null))).assertProblem(409, VARIABLE_VERSION_CONFLICT.getCode());

        variableData.assertUnchanged(target);
    }

    /** 墓碑集合与不存在的编码。 */
    @Test
    void test_businessRuleViolation_replaceDeletedOrAbsent() {
        MockedVariable deleted = variables.mock(VariableBuilder.textCollection().status(VariableStatus.DELETED)
            .member("A", null));
        String absent = variables.newCode();

        replace(deleted.code(), members("1", member("B", null))).assertNotFound();
        replace(absent, members("1", member("B", null))).assertNotFound();

        variableData.assertUnchanged(deleted);
        variableData.assertAbsent(absent);
    }

    /** 非集合变量不能替换成员。 */
    @Test
    void test_businessRuleViolation_replaceNotCollection() {
        MockedVariable constant = variables.mock(VariableBuilder.constantText("v"));

        replace(constant.code(), members("1", member("B", null))).assertProblem(409, VARIABLE_TYPE_MISMATCH.getCode());

        variableData.assertUnchanged(constant);
    }

    /** 系统集合不开放运营维护成员。 */
    @Test
    void test_businessRuleViolation_replaceSystemCollection() {
        MockedVariable system = variables.mock(VariableBuilder.collection(STRING, TOKEN).systemDefinition()
            .member("PARTNER", null));

        replace(system.code(), members("1", member("CLIENT", null))).assertProblem(403,
            SYSTEM_VARIABLE_READ_ONLY.getCode());

        variableData.assertUnchanged(system);
    }

    /** 同一集合的资源锁被其他写入者持有时返回并发冲突。 */
    @Test
    void test_businessRuleViolation_replaceResourceLocked() {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().member("A", null));
        variables.holdLock(target.code());

        replace(target.code(), members("1", member("B", null))).assertProblem(409, CONCURRENT_REQUEST.getCode());

        variableData.assertUnchanged(target);
    }

    /** 字段约束由入口 Bean Validation 拒绝并定位字段。 */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidReplaceFields")
    void test_invalidParameter_replaceFields(String field, String scenario, ReplaceMembersRequestBody request) {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().member("A", null));

        replace(target.code(), request).assertProblem(422, VALIDATION_ERROR.getCode()).assertFieldError(field);

        variableData.assertUnchanged(target);
    }

    /** 不符合严格 JSON 合同的报文、版本溢出、Query 与路径编码统一返回 400。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedReplaceBodies")
    void test_invalidParameter_replaceMalformed(String scenario, String pathSuffix, Function<ObjectNode, String> raw) {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().member("A", null));
        ObjectNode valid = objectMapper.valueToTree(members("1", member("B", "bee")));

        replace(headers(), membersPath(target.code()) + pathSuffix, raw.apply(valid)).assertProblem(400,
            INVALID_REQUEST.getCode());

        variableData.assertUnchanged(target);
    }

    /** 请求体超过 64 KiB。 */
    @Test
    void test_invalidParameter_replaceBodyTooLarge() {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().member("A", null));

        replace(headers(), target.code(), padded(members("1", member("B", null)), 65537)).assertProblem(413,
            INVALID_REQUEST.getCode());

        variableData.assertUnchanged(target);
    }

    /** 非 JSON 请求在进入合同前以 415 拒绝。 */
    @Test
    void test_invalidParameter_replaceUnsupportedMediaType() {
        MockedVariable target = variables.mock(VariableBuilder.textCollection().member("A", null));
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.TEXT_PLAIN);

        replace(headers, target.code(), objectMapper.writeValueAsString(members("1", member("B", null))))
            .assertProblem(415, UNSUPPORTED_MEDIA_TYPE.getCode());

        variableData.assertUnchanged(target);
    }

    static Stream<Arguments> typedCollections() {
        return Stream.of(typed("NUMBER", () -> VariableBuilder.collection(NUMBER, NONE).numericUnit(SCALAR)
            .member("-1.50000000", "neg").member("100.00000000", null)),
            typed("BOOLEAN", () -> VariableBuilder.collection(BOOLEAN, NONE).member(Boolean.TRUE, "yes")
                .member(Boolean.FALSE, "no")),
            typed("DATETIME", () -> VariableBuilder.collection(DATETIME, NONE).member("2026-01-01T00:00:00.000000Z", null)
                .member("2026-12-31T23:59:59.999999Z", "end")),
            typed("DURATION", () -> VariableBuilder.collection(DURATION, NONE).member("0", "zero").member("60000", null)));
    }

    static Stream<Arguments> replacements() {
        return Stream.of(
            replacement("STRING TEXT 保留大小写、空格与前导零", VariableBuilder::textCollection,
                List.of(member("A", " Alpha "), member("A ", null), member("a", "lower"), member("001", null)),
                () -> VariableBuilder.textCollection().member("A", "Alpha").member("A ", null).member("a", "lower")
                    .member("001", null)),
            replacement("STRING TOKEN", () -> VariableBuilder.collection(STRING, TOKEN),
                List.of(member("LOW", null), member("HIGH_2", "高")),
                () -> VariableBuilder.collection(STRING, TOKEN).member("LOW", null).member("HIGH_2", "高")),
            replacement("STRING BCP47 规范大小写", () -> VariableBuilder.collection(STRING, BCP47),
                List.of(member("en-us", null), member("ZH-hans-cn", "中文")),
                () -> VariableBuilder.collection(STRING, BCP47).member("en-US", null).member("zh-Hans-CN", "中文")),
            replacement("NUMBER 补齐八位小数", () -> VariableBuilder.collection(NUMBER, NONE).numericUnit(SCALAR),
                List.of(member("1.5", null), member("-2", "minus"), member("0", null)),
                () -> VariableBuilder.collection(NUMBER, NONE).numericUnit(SCALAR).member("1.50000000", null)
                    .member("-2.00000000", "minus").member("0.00000000", null)),
            replacement("BOOLEAN", () -> VariableBuilder.collection(BOOLEAN, NONE),
                List.of(member(Boolean.FALSE, "no"), member(Boolean.TRUE, null)),
                () -> VariableBuilder.collection(BOOLEAN, NONE).member(Boolean.FALSE, "no").member(Boolean.TRUE, null)),
            replacement("DATETIME", () -> VariableBuilder.collection(DATETIME, NONE),
                List.of(member("2026-01-01T00:00:00.000001Z", null)),
                () -> VariableBuilder.collection(DATETIME, NONE).member("2026-01-01T00:00:00.000001Z", null)),
            replacement("DURATION", () -> VariableBuilder.collection(DURATION, NONE),
                List.of(member("60000", null), member("9223372036854775807", "max")),
                () -> VariableBuilder.collection(DURATION, NONE).member("60000", null).member("9223372036854775807", "max")));
    }

    static Stream<Arguments> invalidMembers() {
        Supplier<VariableBuilder> text = VariableBuilder::textCollection;
        Supplier<VariableBuilder> number = () -> VariableBuilder.collection(NUMBER, NONE).numericUnit(SCALAR);
        return Stream.of(invalid("字符串完全重复", text, List.of(member("A", null), member("A", "again"))),
            invalid("数字规范值重复", number, List.of(member("1", null), member("1.00000000", null))),
            invalid("BCP47 规范化后重复", () -> VariableBuilder.collection(STRING, BCP47),
                List.of(member("en-US", null), member("en-us", null))),
            invalid("格式不符", () -> VariableBuilder.collection(STRING, UPPER_ALPHA_2),
                List.of(member("AE", null), member("ae", null))),
            invalid("BOOLEAN 使用字符串", () -> VariableBuilder.collection(BOOLEAN, NONE), List.of(member("true", null))),
            invalid("NUMBER 使用 Boolean", number, List.of(member(Boolean.TRUE, null))),
            invalid("NUMBER 科学计数法", number, List.of(member("1E2", null))),
            invalid("字符串为空", text, List.of(member("", null))),
            invalid("字符串含控制字符", text, List.of(member("a\nb", null))),
            invalid("显示名称为空白", text, List.of(member("A", "  "))),
            invalid("显示名称超过 255 字符", text, List.of(member("A", "名".repeat(256)))),
            invalid("显示名称含控制字符", text, List.of(member("A", "a\tb"))));
    }

    static Stream<Arguments> invalidListQueries() {
        return Stream.of(Arguments.of("page 为 0", "page=0"), Arguments.of("page 超过 10000", "page=10001&expectedVersion=1"),
            Arguments.of("size 为 0", "size=0"), Arguments.of("size 超过 100", "size=101"),
            Arguments.of("size 非数字", "size=ten"), Arguments.of("expectedVersion 为 0", "expectedVersion=0"),
            Arguments.of("expectedVersion 非数字", "expectedVersion=v1"),
            Arguments.of("expectedVersion 超过 BIGINT", "expectedVersion=9223372036854775808"),
            Arguments.of("关键字超过 128 字符", "keyword=" + "k".repeat(129)),
            Arguments.of("关键字含控制字符", "keyword=a\tb"), Arguments.of("参数为空值", "keyword="),
            Arguments.of("参数重复", "size=1&size=1"), Arguments.of("未知参数", "status=ACTIVE"));
    }

    static Stream<Arguments> invalidReplaceFields() {
        return Stream.of(Arguments.of("expectedVersion", "缺失", new ReplaceMembersRequestBody(null, List.of())),
            Arguments.of("expectedVersion", "为 0", new ReplaceMembersRequestBody("0", List.of())),
            Arguments.of("expectedVersion", "前导零", new ReplaceMembersRequestBody("01", List.of())),
            Arguments.of("members", "缺失", new ReplaceMembersRequestBody("1", null)),
            Arguments.of("members", "超过 1000 项",
                new ReplaceMembersRequestBody("1", Collections.nCopies(1001, member("A", null)))),
            Arguments.of("members[0].value", "成员值缺失", members("1", member(null, "no value"))));
    }

    static Stream<Arguments> malformedReplaceBodies() {
        return Stream.of(malformed("根未知字段", "", body -> body.put("variableCode", "X").toString()),
            malformed("成员未知字段", "", body -> {
                ((ObjectNode) body.withArray("members").get(0)).put("sortOrder", 1);
                return body.toString();
            }), malformed("成员缺少显示名称键", "", body -> {
                ((ObjectNode) body.withArray("members").get(0)).remove("displayName");
                return body.toString();
            }), malformed("成员值为数字", "", body -> {
                ((ObjectNode) body.withArray("members").get(0)).put("value", 1);
                return body.toString();
            }), malformed("成员为 null", "", body -> {
                body.putArray("members").addNull();
                return body.toString();
            }), malformed("成员为字符串", "", body -> {
                body.putArray("members").add("A");
                return body.toString();
            }), malformed("members 不是数组", "", body -> body.put("members", "A").toString()),
            malformed("重复字段键", "", body -> body.toString().replaceFirst("\\{", "{\"expectedVersion\":\"1\",")),
            malformed("expectedVersion 为数字", "", body -> body.put("expectedVersion", 1).toString()),
            malformed("版本超过 BIGINT", "", body -> body.put("expectedVersion", "9223372036854775808").toString()),
            malformed("携带 Query 参数", "?expectedVersion=1", ObjectNode::toString));
    }

    /** 替换成功：响应、根（版本加一、操作者、命令时间）与成员行完全一致。 */
    private void assertReplaced(MockedVariable target, List<VariableBuilder.MemberSpec> members, Instant from,
        VariableMembersResponseChecker response) {
        VariableBuilder expected = target.spec().updated(ACTOR_ID).members(members);
        Instant time = variableData.assertStored(target.code(), expected);
        variableData.assertMembers(target.code(), expected, time);
        response.assertReplaced(target.code(), target.version() + 1, members.size(), time);
        assertFalse(time.isBefore(from), "更新时间应为本次命令时间");
    }

    /** 与集合类型一致的一个合法原成员值。 */
    private static Object sample(VariableBuilder collection) {
        return switch (collection.valueType()) {
            case NUMBER -> "7.00000000";
            case BOOLEAN -> Boolean.TRUE;
            case DATETIME -> "2025-01-01T00:00:00.000000Z";
            case DURATION -> "1";
            case STRING -> switch (collection.formatCode()) {
                case TOKEN -> "OLD";
                case BCP47 -> "fr-FR";
                case UPPER_ALPHA_2 -> "QA";
                default -> "old";
            };
        };
    }

    private String padded(ReplaceMembersRequestBody body, int bytes) {
        String json = objectMapper.writeValueAsString(body);
        return json + " ".repeat(bytes - json.getBytes(StandardCharsets.UTF_8).length);
    }

    private static String membersPath(String code) {
        return variablePath(code) + "/members";
    }

    private static Arguments typed(String scenario, Supplier<VariableBuilder> spec) {
        return Arguments.of(scenario, spec);
    }

    private static Arguments replacement(String scenario, Supplier<VariableBuilder> collection, List<MemberRequest> request,
        Supplier<VariableBuilder> expected) {
        return Arguments.of(scenario, collection, request, expected);
    }

    private static Arguments invalid(String scenario, Supplier<VariableBuilder> collection, List<MemberRequest> request) {
        return Arguments.of(scenario, collection, request);
    }

    private static Arguments malformed(String scenario, String pathSuffix, Function<ObjectNode, String> raw) {
        return Arguments.of(scenario, pathSuffix, raw);
    }

    private VariableMembersResponseChecker list(MockedVariable collection, String query) {
        return invokeList(membersPath(collection.code()) + (query.isEmpty() ? "" : "?" + query));
    }

    private VariableMembersResponseChecker invokeList(String uri) {
        HttpHeaders headers = headers();
        return new VariableMembersResponseChecker(http.get(uri, headers), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    private VariableMembersResponseChecker replace(String code, ReplaceMembersRequestBody body) {
        return replace(headers(), membersPath(code), body);
    }

    private VariableMembersResponseChecker replace(HttpHeaders headers, String codeOrPath, Object body) {
        String path = codeOrPath.startsWith("/") ? codeOrPath : membersPath(codeOrPath);
        return new VariableMembersResponseChecker(http.exchange(HttpMethod.PUT, path, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }
}
