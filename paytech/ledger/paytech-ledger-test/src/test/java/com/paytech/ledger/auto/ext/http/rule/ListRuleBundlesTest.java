package com.paytech.ledger.auto.ext.http.rule;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.AccountingRuleBundleApi;
import com.paytech.ledger.test.base.AbstractRuleBundleApiTest;

import tools.jackson.databind.JsonNode;

/**
 * 规则包列表：初始化的两个规则包按代码升序，带状态与当前生效版本；批准生效后当前版本与开始时间随之切换，草稿不影响。
 *
 * @see AccountingRuleBundleApi#listRuleBundles()
 */
class ListRuleBundlesTest extends AbstractRuleBundleApiTest {
    /** 初始化 DML 预置的调整规则包代码。 */
    private static final String ADJUSTMENT_BUNDLE_CODE = "STANDARD_ADJUSTMENT";

    /** 初始化版本的开始适用时间。 */
    private static final Instant INITIAL_EFFECTIVE_FROM = Instant.parse("2000-01-01T00:00:00Z");

    /** 初始状态：两个规则包均为 ACTIVE、当前第 1 版。 */
    @Test
    void test_listInitialBundles() {
        List<JsonNode> items = items();

        assertEquals(List.of(ADJUSTMENT_BUNDLE_CODE, BUNDLE_CODE),
            items.stream().map(item -> item.path("bundleCode").asString()).toList());
        for (JsonNode item : items) {
            assertAll(item.path("bundleCode").asString(),
                () -> assertEquals(BUNDLE_ACTIVE, item.path("status").asString()),
                () -> assertEquals(String.valueOf(INITIAL_BUNDLE_VER), item.path("currentBundleVer").asString()),
                () -> assertEquals(INITIAL_EFFECTIVE_FROM, Instant.parse(item.path("currentEffectiveFrom").asString())));
        }
    }

    /** 新版本生效后当前版本与开始时间切换；只建草稿时列表不变。 */
    @Test
    void test_currentVersionFollowsActivation() {
        createVersion(version()).assertSuccess(201);
        assertEquals(String.valueOf(INITIAL_BUNDLE_VER), bundle(BUNDLE_CODE).path("currentBundleVer").asString());

        submit(2, MAKER_ID).assertSuccess(200);
        Instant effectiveFrom = Instant.parse(approve(2, CHECKER_ID).assertSuccess(200).text("effectiveFrom"));

        JsonNode current = bundle(BUNDLE_CODE);
        assertAll(() -> assertEquals("2", current.path("currentBundleVer").asString()),
            () -> assertEquals(effectiveFrom, Instant.parse(current.path("currentEffectiveFrom").asString())));
        assertEquals(String.valueOf(INITIAL_BUNDLE_VER), bundle(ADJUSTMENT_BUNDLE_CODE).path("currentBundleVer").asString());
    }

    /** 全部规则包。 */
    List<JsonNode> items() {
        JsonNode items = listBundles().assertSuccess(200).json().path("items");
        assertTrue(items.isArray(), "items");
        return items.valueStream().toList();
    }

    /** 列表中指定代码的规则包；缺失时失败。 */
    JsonNode bundle(String bundleCode) {
        return items().stream().filter(item -> bundleCode.equals(item.path("bundleCode").asString())).findFirst()
            .orElseThrow(() -> new AssertionError("列表缺少规则包 " + bundleCode));
    }
}
