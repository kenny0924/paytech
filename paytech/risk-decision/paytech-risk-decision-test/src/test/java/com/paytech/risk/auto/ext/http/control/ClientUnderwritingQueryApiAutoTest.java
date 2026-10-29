package com.paytech.risk.auto.ext.http.control;

import static com.paytech.foundation.common.error.CommonErrorCode.INVALID_REQUEST;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingOutcome;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.core.enums.UnderwritingStatus;
import com.paytech.risk.control.domain.underwriting.EffectiveUnderwriting;
import com.paytech.risk.control.domainservice.service.ClientUnderwritingQueryService;
import com.paytech.risk.decision.api.control.ClientUnderwritingApi;
import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.test.base.AbstractUnderwritingApiTest;
import com.paytech.risk.test.builder.control.UnderwritingRequestBuilder;
import com.paytech.risk.test.checker.control.UnderwritingResponseChecker;
import com.paytech.risk.test.mapper.control.UnderwritingTestMapper;

/**
 * 承保查询：列表筛选、统计、当前与历史、REQ-04 有效结论读取，以及 GOLIVE_UNDERWRITING_APPROVED 模板正文
 * <li>本类公共数据：四个 Client——A 人工批准（要求保证金、T+5）、B HIGH 待复核、C 草稿、D 已拒绝。</li>
 *
 * @see ClientUnderwritingApi#listUnderwritings(String, String, String, String, String, String, String, String, Integer, Integer)
 * @see ClientUnderwritingApi#getUnderwritingStatistics(String, String)
 * @see ClientUnderwritingApi#getCurrentUnderwriting(String, String, String)
 * @see ClientUnderwritingApi#getUnderwritingHistory(String, String, String)
 * @see ClientUnderwritingQueryService#findEffective(long)
 */
class ClientUnderwritingQueryApiAutoTest extends AbstractUnderwritingApiTest {
    /** 交付的模板文件（相对测试模块目录）。 */
    static final Path TEMPLATE = Path.of("..", "..", "..", "requirements", "20261029", "risk-decision-承保记录", "db",
        "data-query-templates.sql");

    @Autowired
    private ClientUnderwritingQueryService queries;

    private long approvedClient;

    private long pendingClient;

    private long draftClient;

    private long declinedClient;

    private long approvedId;

    /** 每例准备四个状态各一的 Client。 */
    @BeforeEach
    void setUpClassMocks() {
        approvedClient = newClientId();
        UnderwritingDecisionBody deposit = UnderwritingRequestBuilder.decision();
        deposit.setRequireDeposit(Boolean.TRUE);
        deposit.setDepositAmount("5000");
        deposit.setDepositCurrency("USD");
        deposit.setDepositBlocksCollection(Boolean.TRUE);
        UnderwritingInputBody alpha = named("Alpha Foods", "CL-Q-ALPHA", UnderwritingRiskLevel.LOW);
        UnderwritingResponseChecker draft = createDraft(approvedClient, alpha, deposit);
        approvedId = submit(draft.id(), draft.version(), alpha, deposit, UnderwritingOutcome.APPROVE, null).id();

        pendingClient = newClientId();
        UnderwritingInputBody beta = named("Beta Travel", "CL-Q-BETA", UnderwritingRiskLevel.HIGH);
        UnderwritingResponseChecker high = createDraft(pendingClient, beta, UnderwritingRequestBuilder.decision());
        submit(high.id(), high.version(), beta, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE, null);

        draftClient = newClientId();
        createDraft(draftClient, named("Gamma Games", "CL-Q-GAMMA", UnderwritingRiskLevel.MEDIUM), null);

        declinedClient = newClientId();
        UnderwritingInputBody delta = named("Delta Tickets", "CL-Q-DELTA", UnderwritingRiskLevel.MEDIUM);
        UnderwritingResponseChecker declined = createDraft(declinedClient, delta, UnderwritingRequestBuilder.decision());
        submit(declined.id(), declined.version(), delta, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.DECLINE,
            "资料不全");
    }

    /** 列表按更新时间倒序，按状态、CRR、来源、关键字、Client 与待我复核筛选。 */
    @Test
    void test_normalFlow_listFilters() {
        get(UNDERWRITER, ROOT + "/underwritings").assertPage(4, "clientNo",
            List.of("CL-Q-DELTA", "CL-Q-GAMMA", "CL-Q-BETA", "CL-Q-ALPHA"));
        get(UNDERWRITER, ROOT + "/underwritings?status=" + UnderwritingStatus.SUBMITTED.getCode())
            .assertPage(1, "clientNo", List.of("CL-Q-BETA"));
        get(UNDERWRITER, ROOT + "/underwritings?crr=" + UnderwritingRiskLevel.MEDIUM.getCode())
            .assertPage(2, "clientNo", List.of("CL-Q-DELTA", "CL-Q-GAMMA"));
        get(UNDERWRITER, ROOT + "/underwritings?decisionSource=" + UnderwritingDecisionSource.MANUAL.getCode())
            .assertPage(2, "clientNo", List.of("CL-Q-DELTA", "CL-Q-ALPHA"));
        get(UNDERWRITER, ROOT + "/underwritings?keyword=Foods").assertPage(1, "clientNo", List.of("CL-Q-ALPHA"));
        get(UNDERWRITER, ROOT + "/underwritings?keyword=CL-Q-GAMMA").assertPage(1, "clientNo", List.of("CL-Q-GAMMA"));
        get(UNDERWRITER, ROOT + "/underwritings?keyword=" + declinedClient).assertPage(1, "clientNo", List.of("CL-Q-DELTA"));
        get(UNDERWRITER, ROOT + "/underwritings?clientId=" + approvedClient)
            .assertPage(1, "requireDeposit", List.of("true"));
        get(UNDERWRITER, ROOT + "/underwritings?pendingReviewFor=" + RISK_LEAD).assertPage(1, "clientNo", List.of("CL-Q-BETA"));
        get(UNDERWRITER, ROOT + "/underwritings?pendingReviewFor=" + UNDERWRITER).assertPage(0, "clientNo", List.of());
        get(UNDERWRITER, ROOT + "/underwritings?size=10&page=2").assertPage(4, "clientNo", List.of());
    }

    /** "全部"每个 Client 只列最新版本：A 修订批准后只见 v2，D 重建草稿后只见 v2；total 同口径，叠加筛选只看最新版本。 */
    @Test
    void test_normalFlow_listLatestVersionPerClient() {
        reviseAndApproveAlpha();
        createDraft(declinedClient, named("Delta Tickets", "CL-Q-DELTA", UnderwritingRiskLevel.MEDIUM), null);
        get(UNDERWRITER, ROOT + "/underwritings").assertPage(4, "clientNo",
            List.of("CL-Q-DELTA", "CL-Q-ALPHA", "CL-Q-GAMMA", "CL-Q-BETA"));
        get(UNDERWRITER, ROOT + "/underwritings").assertPage(4, "versionNo", List.of("2", "2", "1", "1"));
        get(UNDERWRITER, ROOT + "/underwritings?keyword=Delta").assertPage(1, "status",
            List.of(UnderwritingStatus.DRAFT.getCode()));
        get(UNDERWRITER, ROOT + "/underwritings?decisionSource=" + UnderwritingDecisionSource.MANUAL.getCode())
            .assertPage(1, "clientNo", List.of("CL-Q-ALPHA"));
        get(UNDERWRITER, ROOT + "/underwritings?clientId=" + approvedClient).assertPage(1, "versionNo", List.of("2"));
    }

    /** 按状态筛选列出该状态全部版本：已作废页签见 A v1，已拒绝页签仍见 D v1（虽已有更新的草稿）。 */
    @Test
    void test_normalFlow_listStatusTabsIncludeEarlierVersions() {
        reviseAndApproveAlpha();
        createDraft(declinedClient, named("Delta Tickets", "CL-Q-DELTA", UnderwritingRiskLevel.MEDIUM), null);
        get(UNDERWRITER, ROOT + "/underwritings?status=" + UnderwritingStatus.SUPERSEDED.getCode())
            .assertPage(1, "underwritingId", List.of(String.valueOf(approvedId)));
        get(UNDERWRITER, ROOT + "/underwritings?status=" + UnderwritingStatus.DECLINED.getCode())
            .assertPage(1, "versionNo", List.of("1"));
        get(UNDERWRITER, ROOT + "/underwritings?status=" + UnderwritingStatus.DRAFT.getCode())
            .assertPage(2, "clientNo", List.of("CL-Q-DELTA", "CL-Q-GAMMA"));
        get(UNDERWRITER, ROOT + "/underwritings?status=" + UnderwritingStatus.APPROVED.getCode() + "&keyword=Foods")
            .assertPage(1, "versionNo", List.of("2"));
    }

    /** 统计：生效 1、草稿 1、待复核 1、本月批准 1、本月拒绝 1。 */
    @Test
    void test_normalFlow_statistics() {
        get(UNDERWRITER, ROOT + "/underwriting-statistics").assertStatistics(1, 1, 1, 1, 1);
    }

    /** 当前与历史：A 有效 v1；B 进行中 v1；历史版本号倒序。 */
    @Test
    void test_normalFlow_currentAndHistory() {
        get(UNDERWRITER, ROOT + "/clients/" + approvedClient + "/underwriting").assertCurrent(1, null, 1);
        get(UNDERWRITER, ROOT + "/clients/" + pendingClient + "/underwriting").assertCurrent(null, 1, 1);
        get(UNDERWRITER, ROOT + "/clients/" + newClientId() + "/underwriting").assertCurrent(null, null, null);
        createDraft(declinedClient, named("Delta Tickets", "CL-Q-DELTA", UnderwritingRiskLevel.MEDIUM), null);
        get(UNDERWRITER, ROOT + "/clients/" + declinedClient + "/underwritings")
            .assertHistory(List.of(UnderwritingStatus.DRAFT.getCode(), UnderwritingStatus.DECLINED.getCode()));
    }

    /** 筛选 Code 或 ID 非法返回 400。 */
    @Test
    void test_invalidParameter_filters() {
        get(UNDERWRITER, ROOT + "/underwritings?status=OPEN").assertProblem(400, INVALID_REQUEST.getCode());
        get(UNDERWRITER, ROOT + "/underwritings?clientId=abc").assertProblem(400, INVALID_REQUEST.getCode());
        get(UNDERWRITER, ROOT + "/underwritings?size=7").assertProblem(400, INVALID_REQUEST.getCode());
        get(UNDERWRITER, ROOT + "/underwritings/abc").assertProblem(400, INVALID_REQUEST.getCode());
    }

    /** REQ-04 同进程读取：有有效结论返回摘要，没有返回空。 */
    @Test
    void test_normalFlow_findEffective() {
        EffectiveUnderwriting effective = queries.findEffective(approvedClient).orElseThrow();
        assertAll("有效结论",
            () -> assertEquals(approvedId, effective.getUnderwritingId()),
            () -> assertEquals(1, effective.getVersionNo()),
            () -> assertEquals(30, effective.getObservationDays()),
            () -> assertEquals(100, effective.getObservationTransactions()),
            () -> assertTrue(effective.isRequireDeposit()),
            () -> assertEquals(0, new BigDecimal("5000").compareTo(effective.getDepositAmount())),
            () -> assertEquals("USD", effective.getDepositCurrency()),
            () -> assertTrue(effective.isDepositBlocksCollection()),
            () -> assertEquals(5, effective.getSuggestedSettlementDays()),
            () -> assertTrue(effective.getDecidedTime() != null));
        assertTrue(queries.findEffective(pendingClient).isEmpty());
        assertTrue(queries.findEffective(declinedClient).isEmpty());
    }

    /** 模板正文：批准 satisfied=1 与四个输出；待复核 / 拒绝 / 未开始 satisfied=0 与原因码；始终单行。 */
    @Test
    void test_normalFlow_goLiveTemplate() throws IOException {
        String template = templateBody();
        Map<String, Object> approved = single(template, approvedClient);
        assertAll("已批准",
            () -> assertEquals(1L, ((Number) approved.get("satisfied")).longValue()),
            () -> assertTrue(UnderwritingTestMapper.flag(approved.get("out_requireDeposit"))),
            () -> assertTrue(UnderwritingTestMapper.flag(approved.get("out_depositBlocksCollection"))),
            () -> assertFalse(UnderwritingTestMapper.flag(approved.get("out_highRiskMcc"))),
            () -> assertEquals(5L, ((Number) approved.get("out_suggestedSettlementDays")).longValue()),
            () -> assertNull(approved.get("missing")));
        assertMissing(template, pendingClient, "UNDERWRITING_PENDING_REVIEW");
        assertMissing(template, declinedClient, "UNDERWRITING_DECLINED");
        assertMissing(template, draftClient, "UNDERWRITING_NOT_STARTED");
        assertMissing(template, newClientId(), "UNDERWRITING_NOT_STARTED");
    }

    /** 未满足：satisfied=0、输出为空、原因码。 */
    void assertMissing(String template, long clientId, String missing) {
        Map<String, Object> row = single(template, clientId);
        assertAll("未满足 " + missing,
            () -> assertEquals(0L, ((Number) row.get("satisfied")).longValue()),
            () -> assertNull(row.get("out_requireDeposit")),
            () -> assertNull(row.get("out_suggestedSettlementDays")),
            () -> assertEquals(missing, row.get("missing")));
    }

    /** 模板必须恰好返回一行。 */
    Map<String, Object> single(String template, long clientId) {
        List<Map<String, Object>> rows = store.runTemplate(template, clientId);
        assertEquals(1, rows.size(), "模板须返回单行");
        return rows.getFirst();
    }

    /** 交付文件中分隔线之后的模板正文。 */
    static String templateBody() throws IOException {
        String text = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        return text.substring(text.indexOf('\n', text.indexOf("-- template --")) + 1);
    }

    /** 指定名称、编号与 CRR 的输入。 */
    /** A 基于有效 v1 修订并批准 v2，v1 变为 SUPERSEDED。 */
    private void reviseAndApproveAlpha() {
        UnderwritingInputBody alpha = named("Alpha Foods", "CL-Q-ALPHA", UnderwritingRiskLevel.LOW);
        int version = get(UNDERWRITER, ROOT + "/underwritings/" + approvedId).version();
        UnderwritingResponseChecker draft = post(UNDERWRITER, ROOT + "/underwritings/" + approvedId + "/revision",
            UnderwritingRequestBuilder.revise(version, "流水上调", alpha)).assertUnderwriting(201, UnderwritingStatus.DRAFT.getCode(),
            UnderwritingRiskLevel.LOW.getCode());
        submit(draft.id(), draft.version(), alpha, UnderwritingRequestBuilder.decision(), UnderwritingOutcome.APPROVE, null)
            .assertUnderwriting(200, UnderwritingStatus.APPROVED.getCode(), UnderwritingRiskLevel.LOW.getCode());
    }

    static UnderwritingInputBody named(String displayName, String clientNo, UnderwritingRiskLevel crr) {
        UnderwritingInputBody input = UnderwritingRequestBuilder.input(crr);
        input.setClientDisplayName(displayName);
        input.setClientNo(clientNo);
        return input;
    }
}
