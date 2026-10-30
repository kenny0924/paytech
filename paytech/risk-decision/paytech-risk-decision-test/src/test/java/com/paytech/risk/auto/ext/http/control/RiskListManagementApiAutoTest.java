package com.paytech.risk.auto.ext.http.control;

import static com.paytech.foundation.common.error.CommonErrorCode.BUSINESS_CHECK_FAILED;
import static com.paytech.foundation.common.error.CommonErrorCode.CONCURRENT_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.foundation.common.error.CommonErrorCode.VALIDATION_ERROR;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.risk.decision.api.control.RiskListManagementApi;
import com.paytech.risk.decision.api.control.request.AddRiskListEntryRequestBody;
import com.paytech.risk.decision.api.control.request.CreateRiskListRequestBody;
import com.paytech.risk.decision.api.control.request.DeactivateRiskListEntryRequestBody;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.RiskListEntrySource;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.test.base.AbstractPaymentRiskApiTest;
import com.paytech.risk.test.checker.control.RiskListResponseChecker;
import com.paytech.risk.test.mapper.control.LimitTierTestMapper;
import com.paytech.risk.test.mocker.control.LimitTierMocker;

/**
 * 运营名单维护
 * <li>正常流程：创建名单、添加成员（主体键规范化并脱敏返回）、按主体查询、失效成员并记录失效人与原因。</li>
 * <li>参数：主体取值不是合法 IP 字面量、成员 ID 非法返回 400；字段缺失、格式不符或主体类型不在一期范围返回 422。</li>
 * <li>业务规则：名单编码重复、生效区间倒置、成员重复失效返回 400；同主体（含 IPv6 同 /64）已有生效成员返回 409。</li>
 * <li>无数据：名单或成员不存在返回 400。</li>
 * <li>限额档位名单（REQ-04）：四张 TIER 名单是初始化目录，人工增删成员返回 400，不能创建 TIER 用途名单。</li>
 *
 * @see RiskListManagementApi
 */
class RiskListManagementApiAutoTest extends AbstractPaymentRiskApiTest {
    private static final String LISTS = "/risk-lists/v1/lists";

    private String ipList;

    @Autowired
    private LimitTierMocker tierMocker;

    @Autowired
    private LimitTierTestMapper tierStore;

    /** 每例新建一个 IP 黑名单。 */
    @BeforeEach
    void setUpClassMocks() {
        ipList = newCode("T_IP_LIST");
        createList(ipList, RiskSubjectType.IP_ADDRESS.getCode(), RiskListPurpose.BLOCK.getCode());
    }

    /** 创建名单后可在分页中查到。 */
    @Test
    void test_normalFlow_createList() {
        String code = newCode("T_BIN_LIST");

        post(LISTS, createBody(code, RiskSubjectType.CARD_BIN, RiskListPurpose.ALLOW))
            .assertList(code, RiskSubjectType.CARD_BIN.getCode(), RiskListPurpose.ALLOW.getCode());

        get(LISTS + "?size=10").assertPageValues(6, "listCode", java.util.Set.of(code, ipList,
            LimitTier.VERIFICATION.listCode(), LimitTier.OBSERVATION.listCode(), LimitTier.STANDARD.listCode(),
            LimitTier.RESTRICTED.listCode()));
    }

    /** 添加 IPv4 与 IPv6 成员：主体键规范化并脱敏；按原值查询命中；失效后状态为 RELEASED 并记录审计。 */
    @Test
    void test_normalFlow_entryLifecycle() {
        long ipv4 = post(entries(ipList), entryBody("203.0.113.10"))
            .assertEntry(RiskListEntryStatus.ACTIVE.getCode(), "203.0.*", RiskListEntrySource.MANUAL.getCode());
        post(entries(ipList), entryBody("2001:db8:abcd:12::1"))
            .assertEntry(RiskListEntryStatus.ACTIVE.getCode(), "2001:db8:*", RiskListEntrySource.MANUAL.getCode());
        get(entries(ipList) + "?subjectValue=2001:db8:abcd:12:ffff::9").assertPage(1, "maskedSubjectKey",
            List.of("2001:db8:*"));

        post(entries(ipList) + "/" + ipv4 + "/deactivate", deactivateBody("误加"))
            .assertDeactivated(ACTOR_ID, "误加")
            .assertEntry(RiskListEntryStatus.RELEASED.getCode(), "203.0.*", RiskListEntrySource.MANUAL.getCode());

        Map<String, Object> row = store.listEntry(ipv4);
        assertAll("落库成员",
            () -> assertEquals("203.0.113.10", row.get("subject_key")),
            () -> assertEquals(RiskListEntryStatus.RELEASED.getCode(), row.get("status")),
            () -> assertEquals(ACTOR_ID, ((Number)row.get("created_by")).longValue()),
            () -> assertEquals(ACTOR_ID, ((Number)row.get("deactivated_by")).longValue()));
        get(entries(ipList) + "?status=" + RiskListEntryStatus.ACTIVE.getCode()).assertPage(1, "maskedSubjectKey",
            List.of("2001:db8:*"));
    }

    /** 成员失效后同一主体可再次加入。 */
    @Test
    void test_edgeCase_readdAfterDeactivate() {
        long first = post(entries(ipList), entryBody("203.0.113.10"))
            .assertEntry(RiskListEntryStatus.ACTIVE.getCode(), "203.0.*", RiskListEntrySource.MANUAL.getCode());
        post(entries(ipList) + "/" + first + "/deactivate", deactivateBody("解除"))
            .assertEntry(RiskListEntryStatus.RELEASED.getCode(), "203.0.*", RiskListEntrySource.MANUAL.getCode());

        post(entries(ipList), entryBody("203.0.113.10"))
            .assertEntry(RiskListEntryStatus.ACTIVE.getCode(), "203.0.*", RiskListEntrySource.MANUAL.getCode());
    }

    /** 主体取值不是合法 IP 字面量或成员 ID 非法。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEntries")
    void test_invalidParameter(String scenario, String path, Object body) {
        post(path.replace("{list}", ipList), body).assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** 请求体字段缺失、格式不符或主体类型不在一期范围。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contractViolations")
    void test_invalidParameter_contract(String scenario, String path, Object body) {
        post(path.replace("{list}", ipList), body).assertProblem(422, VALIDATION_ERROR.getCode());
    }

    /** 名单编码重复。 */
    @Test
    void test_businessRuleViolation_createList() {
        post(LISTS, createBody(ipList, RiskSubjectType.IP_ADDRESS, RiskListPurpose.BLOCK))
            .assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
    }

    /** 同主体已有生效成员（IPv6 按 /64 视为同一主体）返回 409；生效区间倒置与重复失效返回 400。 */
    @Test
    void test_businessRuleViolation_entry() {
        long entry = post(entries(ipList), entryBody("2001:db8:abcd:12::1"))
            .assertEntry(RiskListEntryStatus.ACTIVE.getCode(), "2001:db8:*", RiskListEntrySource.MANUAL.getCode());

        post(entries(ipList), entryBody("2001:db8:abcd:12::77")).assertProblem(409, CONCURRENT_REQUEST.getCode());
        AddRiskListEntryRequestBody reversed = entryBody("203.0.113.20");
        reversed.setEffectiveFrom("2026-10-02T00:00:00Z");
        reversed.setEffectiveTo("2026-10-01T00:00:00Z");
        post(entries(ipList), reversed).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        post(entries(ipList) + "/" + entry + "/deactivate", deactivateBody("解除"))
            .assertEntry(RiskListEntryStatus.RELEASED.getCode(), "2001:db8:*", RiskListEntrySource.MANUAL.getCode());
        post(entries(ipList) + "/" + entry + "/deactivate", deactivateBody("再次解除"))
            .assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
    }

    /** 限额档位名单由系统维护：人工添加、失效成员返回 400 且成员不变；创建 TIER 用途的名单被合同拒绝（422）。 */
    @Test
    void test_businessRuleViolation_tierListMaintenance() {
        long clientId = 81_000_001L;
        tierMocker.mockTier(clientId, LimitTier.STANDARD);
        long entryId = ((Number)tierStore.tierEntries(clientId).getFirst().get("entry_id")).longValue();

        post(entries(LimitTier.STANDARD.listCode()), entryBody("81000002"))
            .assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        post(entries(LimitTier.STANDARD.listCode()) + "/" + entryId + "/deactivate", deactivateBody("人工移出"))
            .assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        CreateRiskListRequestBody tierList = createBody("T_TIER_LIST", RiskSubjectType.CLIENT, RiskListPurpose.BLOCK);
        tierList.setPurpose(RiskListPurpose.TIER.getCode());
        post(LISTS, tierList).assertProblem(422, VALIDATION_ERROR.getCode());

        assertEquals(List.of(LimitTier.STANDARD.listCode()), tierStore.activeTierLists(clientId));
        assertEquals(1, tierStore.tierEntries(clientId).size(), "没有新增成员");
    }

    /** 名单或成员不存在。 */
    @Test
    void test_noDataFound() {
        post(entries("T_MISSING_LIST"), entryBody("203.0.113.10")).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        post(entries(ipList) + "/9876543210/deactivate", deactivateBody("不存在"))
            .assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
        get(entries("T_MISSING_LIST")).assertProblem(400, BUSINESS_CHECK_FAILED.getCode());
    }

    static Stream<Arguments> invalidEntries() {
        return Stream.of(Arguments.of("IP 非法", "/risk-lists/v1/lists/{list}/entries", entryBody("999.1.1.1")),
            Arguments.of("IP 为域名", "/risk-lists/v1/lists/{list}/entries", entryBody("example.com")),
            Arguments.of("成员 ID 非法", "/risk-lists/v1/lists/{list}/entries/abc/deactivate", deactivateBody("x")));
    }

    static Stream<Arguments> contractViolations() {
        return Stream.of(Arguments.of("主体为空", "/risk-lists/v1/lists/{list}/entries", entryBody("")),
            Arguments.of("缺少原因", "/risk-lists/v1/lists/{list}/entries", new AddRiskListEntryRequestBody()),
            Arguments.of("生效时间格式非法", "/risk-lists/v1/lists/{list}/entries", withEffectiveFrom("2026-10-01")),
            Arguments.of("主体类型不在一期范围", LISTS,
                createBody("T_EMAIL_LIST", RiskSubjectType.EMAIL_TOKEN, RiskListPurpose.BLOCK)));
    }

    /** 带可信操作者的 POST。 */
    private RiskListResponseChecker post(String path, Object body) {
        HttpHeaders headers = headers();
        return new RiskListResponseChecker(http.post(path, headers, body), objectMapper,
            headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** 带可信操作者的 GET。 */
    private RiskListResponseChecker get(String path) {
        HttpHeaders headers = headers();
        return new RiskListResponseChecker(http.get(path, headers), objectMapper, headers.getFirst(ApiHeaderNames.REQUEST_ID));
    }

    /** 名单成员路径。 */
    private static String entries(String listCode) {
        return LISTS + "/" + listCode + "/entries";
    }

    /** 创建名单请求。 */
    private static CreateRiskListRequestBody createBody(String code, RiskSubjectType subjectType, RiskListPurpose purpose) {
        CreateRiskListRequestBody body = new CreateRiskListRequestBody();
        body.setListCode(code);
        body.setListName("测试名单");
        body.setSubjectType(subjectType.getCode());
        body.setPurpose(purpose.getCode());
        return body;
    }

    /** 长期有效的成员请求。 */
    private static AddRiskListEntryRequestBody entryBody(String subjectValue) {
        AddRiskListEntryRequestBody body = new AddRiskListEntryRequestBody();
        body.setSubjectValue(subjectValue);
        body.setReason("测试加入");
        return body;
    }

    /** 生效起点格式非法的成员请求。 */
    private static AddRiskListEntryRequestBody withEffectiveFrom(String effectiveFrom) {
        AddRiskListEntryRequestBody body = entryBody("203.0.113.30");
        body.setEffectiveFrom(effectiveFrom);
        return body;
    }

    /** 失效请求。 */
    private static DeactivateRiskListEntryRequestBody deactivateBody(String reason) {
        DeactivateRiskListEntryRequestBody body = new DeactivateRiskListEntryRequestBody();
        body.setReason(reason);
        return body;
    }
}
