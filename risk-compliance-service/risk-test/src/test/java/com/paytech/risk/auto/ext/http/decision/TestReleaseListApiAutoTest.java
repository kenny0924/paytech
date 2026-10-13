package com.paytech.risk.auto.ext.http.decision;

import static com.paytech.foundation.common.error.CommonErrorCode.INTERNAL_ERROR;
import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static com.paytech.risk.test.builder.decision.CandidateBuilder.CREATE_TIME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.risk.api.decision.DecisionReleaseManagementApi;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.test.builder.decision.CandidateBuilder;
import com.paytech.risk.test.base.MockTestBase;
import com.paytech.risk.test.checker.decision.TestReleaseListResponseChecker;
import com.paytech.risk.test.mocker.decision.CandidateMocker;
import com.paytech.risk.test.mocker.decision.CandidateMocker.MockedCandidate;

/**
 * TEST Candidate 分页列表
 * <li>正常流程：按创建时间、发布 ID 倒序分页；可按决策点编码与 DRAFT/VALIDATING/REJECTED 状态精确筛选；三态检查结果按审计派生。</li>
 * <li>未找到：没有匹配 Candidate 时返回空页与总数 0；PRODUCTION 环境发布不进入 TEST 列表。</li>
 * <li>参数：分页越界或非允许条数、编码/状态非大写目录值、非候选状态与重复状态在查询前拒绝（400）。</li>
 * <li>闭包损坏：页内任一发布根状态越出首期合同、缺少规则集绑定或检查不完整时整页失败关闭（500）。</li>
 *
 * @see DecisionReleaseManagementApi#listCandidates(String, String, Integer, Integer, String, String)
 */
class TestReleaseListApiAutoTest extends MockTestBase {
    private static final String PATH = "/decision-releases/v1/candidates";

    @Autowired
    private CandidateMocker candidates;

    /** 四种候选形态按创建时间倒序返回，三态检查结果分别为 null、null、true、false。 */
    @Test
    void test_normalFlow() {
        MockedCandidate draft = candidates.mockCandidate(CandidateBuilder.draft().createTime(CREATE_TIME));
        MockedCandidate started = candidates.mockCandidate(
            CandidateBuilder.validationStarted().createTime(CREATE_TIME.plusSeconds(10)));
        MockedCandidate passed = candidates.mockCandidate(
            CandidateBuilder.validationPassed().createTime(CREATE_TIME.plusSeconds(20)));
        MockedCandidate rejected = candidates.mockCandidate(
            CandidateBuilder.rejected().createTime(CREATE_TIME.plusSeconds(30)));

        list("").assertPage(1, 10, 4).assertItems(rejected, passed, started, draft);
    }

    /** 状态筛选只返回该状态的 Candidate，总数同步筛选。 */
    @Test
    void test_normalFlow_statusFilter() {
        MockedCandidate draft = candidates.mockCandidate(CandidateBuilder.draft().createTime(CREATE_TIME));
        MockedCandidate started = candidates.mockCandidate(
            CandidateBuilder.validationStarted().createTime(CREATE_TIME.plusSeconds(10)));
        MockedCandidate passed = candidates.mockCandidate(
            CandidateBuilder.validationPassed().createTime(CREATE_TIME.plusSeconds(20)));
        MockedCandidate rejected = candidates.mockCandidate(
            CandidateBuilder.rejected().createTime(CREATE_TIME.plusSeconds(30)));

        list("status=DRAFT").assertPage(1, 10, 1).assertItems(draft);
        list("status=VALIDATING").assertPage(1, 10, 2).assertItems(passed, started);
        list("status=REJECTED").assertPage(1, 10, 1).assertItems(rejected);
    }

    /** 决策点编码精确筛选，可与状态组合；不存在的编码返回空页。 */
    @Test
    void test_normalFlow_pointCodeFilter() {
        MockedCandidate draft = candidates.mockCandidate(CandidateBuilder.draft().createTime(CREATE_TIME));
        MockedCandidate rejected = candidates.mockCandidate(
            CandidateBuilder.rejected().createTime(CREATE_TIME.plusSeconds(30)));

        list("pointCode=CHECKOUT_PREPARE").assertPage(1, 10, 2).assertItems(rejected, draft);
        list("pointCode=CHECKOUT_PREPARE&status=DRAFT").assertPage(1, 10, 1).assertItems(draft);
        list("pointCode=CHECKOUT_PREPAR").assertPage(1, 10, 0).assertItems();
    }

    /** 分页按倒序切分，越界页为空但保留总数；条数可选 20。 */
    @Test
    void test_normalFlow_paging() {
        List<MockedCandidate> mocked = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            mocked.add(candidates.mockCandidate(CandidateBuilder.draft().createTime(CREATE_TIME.plusSeconds(i))));
        }
        MockedCandidate[] newestFirst = mocked.reversed().toArray(MockedCandidate[]::new);

        list("page=1&size=10").assertPage(1, 10, 11).assertItems(Arrays.copyOfRange(newestFirst, 0, 10));
        list("page=2&size=10").assertPage(2, 10, 11).assertItems(newestFirst[10]);
        list("page=3&size=10").assertPage(3, 10, 11).assertItems();
        list("size=20").assertPage(1, 20, 11).assertItems(newestFirst);
        list("page=100000&size=200").assertPage(100000, 200, 11).assertItems();
    }

    /** 创建时间相同时按发布 ID 倒序。 */
    @Test
    void test_edgeCase_sameCreateTimeOrderedByReleaseId() {
        List<MockedCandidate> mocked = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mocked.add(candidates.mockCandidate(CandidateBuilder.draft()));
        }
        mocked.sort(Comparator.comparingLong(MockedCandidate::releaseId).reversed());

        list("").assertPage(1, 10, 3).assertItems(mocked.toArray(MockedCandidate[]::new));
    }

    /** 没有任何 Candidate 时返回空页。 */
    @Test
    void test_noDataFound() {
        list("").assertPage(1, 10, 0).assertItems();
        list("status=REJECTED").assertPage(1, 10, 0).assertItems();
    }

    /** PRODUCTION 环境的发布不属于 TEST 候选列表，也不计入总数。 */
    @Test
    void test_noDataFound_productionReleaseExcluded() {
        MockedCandidate draft = candidates.mockCandidate(CandidateBuilder.draft());
        candidates.mockCandidate(draft.composition(),
            CandidateBuilder.draft().environment(DecisionEnvironment.PRODUCTION).createTime(CREATE_TIME.plusSeconds(1)));

        list("").assertPage(1, 10, 1).assertItems(draft);
        list("status=DRAFT").assertPage(1, 10, 1).assertItems(draft);
    }

    /** 页内出现首期之外状态的 TEST 发布时整页失败关闭。 */
    @Test
    void test_businessRuleViolation_nonCandidateStatusFailsClosed() {
        candidates.mockCandidate(CandidateBuilder.draft());
        candidates.mockCandidate(CandidateBuilder.draft().status(DecisionReleaseStatus.SHADOWING));

        list("").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 页内 Candidate 的决策点版本缺少规则集绑定时整页失败关闭。 */
    @Test
    void test_businessRuleViolation_missingBindingFailsClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.draft());
        candidates.removeBinding(candidate.composition());

        list("").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 页内 REJECTED Candidate 的八项检查不完整时整页失败关闭。 */
    @Test
    void test_businessRuleViolation_partialStaticChecksFailClosed() {
        MockedCandidate candidate = candidates.mockCandidate(CandidateBuilder.rejected());
        candidates.removeAudit(candidate.staticChecks().getFirst());

        list("status=REJECTED").assertProblem(500, INTERNAL_ERROR.getCode());
    }

    /** 筛选不匹配损坏行时不受其影响：损坏的 DRAFT 不在 REJECTED 页内。 */
    @Test
    void test_edgeCase_corruptionOutsideFilterIsNotLoaded() {
        MockedCandidate broken = candidates.mockCandidate(CandidateBuilder.draft());
        candidates.removeBinding(broken.composition());
        MockedCandidate rejected = candidates.mockCandidate(CandidateBuilder.rejected());

        list("status=REJECTED").assertPage(1, 10, 1).assertItems(rejected);
    }

    /** 查询串非法时在查询前拒绝。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidQueries")
    void test_invalidParameter(String scenario, String query) {
        candidates.mockCandidate(CandidateBuilder.draft());

        list(query).assertProblem(400, INVALID_REQUEST.getCode());
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(Arguments.of("page 为 0", "page=0"), Arguments.of("page 为负数", "page=-1"),
            Arguments.of("page 超过 100000", "page=100001"), Arguments.of("page 非数字", "page=one"),
            Arguments.of("size 不在允许值内", "size=15"),
            Arguments.of("size 为 0", "size=0"), Arguments.of("size 超过 200", "size=500"),
            Arguments.of("status 小写", "status=draft"), Arguments.of("status 为非候选状态 ACTIVE", "status=ACTIVE"),
            Arguments.of("status 为未知值", "status=UNKNOWN"), Arguments.of("status 为空值", "status="),
            Arguments.of("status 重复", "status=DRAFT&status=REJECTED"),
            Arguments.of("pointCode 小写", "pointCode=checkout_prepare"),
            Arguments.of("pointCode 为空值", "pointCode="), Arguments.of("pointCode 含连字符", "pointCode=CHECKOUT-PREPARE"),
            Arguments.of("pointCode 超过 64 字符", "pointCode=C" + "A".repeat(64)));
    }

    private TestReleaseListResponseChecker list(String query) {
        String requestId = nextRequestId();
        return new TestReleaseListResponseChecker(
            http.get(query.isEmpty() ? PATH : PATH + "?" + query, headers(requestId)), objectMapper, requestId);
    }

    /** Gateway 转发的 Operations 可信身份。 */
    private static HttpHeaders headers(String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", "70001");
        headers.set("X-Gateway-Session-Id", "risk-test-session");
        return headers;
    }

    private static String nextRequestId() {
        return "risk-release-list-" + UUID.randomUUID();
    }
}
