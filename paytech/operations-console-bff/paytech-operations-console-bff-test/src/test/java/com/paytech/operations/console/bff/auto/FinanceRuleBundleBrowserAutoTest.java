package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_APPROVE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_MANAGE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.FINANCE_READ_PERMISSION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.operations.console.bff.ext.rest.FinanceRuleBundleController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发记账规则包的 8 个端点（A1—A8：规则包列表、详情、版本详情、新建、提交、批准、驳回、作废）；
 * ledger 由 Loopback Simulator 承担
 *
 * @see FinanceRuleBundleController
 * @author Claude
 */
class FinanceRuleBundleBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/finance-rule-bundle-boundary.json";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、路径、命令正文与操作人 Header 准确转发；提交、批准、驳回与作废不带 Body，VOIDED 等状态原样返回。 */
    @ParameterizedTest(name = "{index} 规则包转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        replyAsOwner(ledger, row);

        assertForwarded(ledger, row, send(row));
    }

    /** 当前会话授权失败时不访问 ledger。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        assertDenied(ledger, send(row));
    }

    /** 新建、提交、作废需要 MANAGE，批准与驳回需要 APPROVE。 */
    @ParameterizedTest
    @CsvSource({"A4,READ", "A5,APPROVE", "A8,APPROVE", "A6,MANAGE", "A7,MANAGE"})
    void test_separatedPermissions(String id, String granted) throws Exception {
        platform.grantPermissions(FINANCE_READ_PERMISSION_ID, switch (granted) {
            case "MANAGE" -> FINANCE_MANAGE_PERMISSION_ID;
            case "APPROVE" -> FINANCE_APPROVE_PERMISSION_ID;
            default -> FINANCE_READ_PERMISSION_ID;
        });

        assertDenied(ledger, send(row(BOUNDARY, id)));
    }

    /** 新建、提交、复核与作废的 LedgerErrorCode 拒绝（D4）Code 与默认消息原样到达，不外露 ledger 原文。 */
    @ParameterizedTest(name = "{index} {0} {1}")
    @CsvSource({"A4,RULE_VERSION_IN_PROGRESS", "A4,RULE_LINE_INVALID", "A5,RULE_LINE_INVALID",
        "A5,RULE_SUBMITTER_NOT_MAKER", "A5,RULE_VERSION_STATUS_INVALID", "A6,SELF_REVIEW_FORBIDDEN",
        "A6,RULE_EFFECTIVE_FROM_INVALID", "A6,RULE_VERSION_STATUS_INVALID", "A7,SELF_REVIEW_FORBIDDEN",
        "A8,RULE_SUBMITTER_NOT_MAKER", "A8,RULE_VERSION_STATUS_INVALID"})
    void test_businessRuleViolation(String id, LedgerErrorCode code) throws Exception {
        var row = row(BOUNDARY, id);
        ledger.problem("POST", ownerPath(row), 400, code.getCode());

        assertCodedRejection(code, send(row));
    }

    /** 未登记的业务拒绝保留公共 Code，不外露原文。 */
    @Test
    void test_commonBusinessCheckFailed() throws Exception {
        var row = row(BOUNDARY, "A6");
        ledger.problem("POST", ownerPath(row), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());

        assertCodedRejection(CommonErrorCode.BUSINESS_CHECK_FAILED, send(row));
    }

    /** 列表带 Query、非法规则包代码与版本号、无体命令带 Body、新建缺生效时间或带编制人均在访问 ledger 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "A1");
        assertRejectedLocally(400, ledger, send(list, "?status=ACTIVE", list.path("request"), true));

        resetBetweenCalls();
        var detail = (ObjectNode) row(BOUNDARY, "A2").deepCopy();
        detail.put("path", "/finance/accounting-rule-bundles/v1/transaction_posting");
        assertRejectedLocally(400, ledger, send(detail, "", detail.path("request"), true));

        resetBetweenCalls();
        var version = (ObjectNode) row(BOUNDARY, "A3").deepCopy();
        version.put("path", "/finance/accounting-rule-bundles/v1/TRANSACTION_POSTING/versions/0");
        assertRejectedLocally(400, ledger, send(version, "", version.path("request"), true));

        resetBetweenCalls();
        for (String id : new String[] {"A5", "A6", "A7", "A8"}) {
            assertRejectedLocally(400, ledger, send(row(BOUNDARY, id), "", JSON.createObjectNode(), true));
            resetBetweenCalls();
        }

        var create = row(BOUNDARY, "A4");
        var noEffective = (ObjectNode) create.path("request").deepCopy();
        noEffective.remove("effectiveFrom");
        assertRejectedLocally(422, ledger, send(create, "", noEffective, true));

        resetBetweenCalls();
        var maker = (ObjectNode) create.path("request").deepCopy();
        maker.put("makerId", "10001");
        assertRejectedLocally(400, ledger, send(create, "", maker, true));
    }

    /** 回执规则包代码或版本号与请求不一致、列表或规则行缺失时视为上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var version = row(BOUNDARY, "A3");
        var otherVer = (ObjectNode) version.path("response").deepCopy();
        otherVer.put("bundleVer", "4");
        ledger.reply("GET", ownerPath(version), 200, JSON.writeValueAsString(otherVer));
        assertEquals(502, send(version).statusCode());

        resetBetweenCalls();
        var list = row(BOUNDARY, "A1");
        ledger.reply("GET", ownerPath(list), 200, "{\"items\":null}");
        assertEquals(502, send(list).statusCode());

        resetBetweenCalls();
        var bundle = row(BOUNDARY, "A2");
        var otherBundle = (ObjectNode) bundle.path("response").deepCopy();
        otherBundle.put("bundleCode", "SERVICE_CHARGE_POSTING");
        ledger.reply("GET", ownerPath(bundle), 200, JSON.writeValueAsString(otherBundle));
        assertEquals(502, send(bundle).statusCode());

        resetBetweenCalls();
        var create = row(BOUNDARY, "A4");
        var noLines = (ObjectNode) create.path("response").deepCopy();
        noLines.putNull("lines");
        ledger.reply("POST", ownerPath(create), 201, JSON.writeValueAsString(noLines));
        assertEquals(502, send(create).statusCode());
    }
}
