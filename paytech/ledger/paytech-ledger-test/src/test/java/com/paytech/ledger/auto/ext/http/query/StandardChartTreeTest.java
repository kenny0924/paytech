package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.ChartAccountStatus;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.test.base.MockTestBase;

import tools.jackson.databind.JsonNode;

/**
 * STANDARD 三级科目树完整性（technique 2.1）：19 个一级汇总科目覆盖五类会计要素，26 个二级汇总科目都有一级上级，
 * 29 个三级末级科目都有二级上级；下级编号以上级编号开头且比上级多 2 位、类别与上级一致，共 74 个且全部 ACTIVE；
 * account 账户类型目录的科目编号与科目表一致。
 *
 * @see LedgerSetupQueryApi#listChartAccounts(String, Boolean, String)
 */
class StandardChartTreeTest extends MockTestBase {
    /** technique 2.1 的一级汇总科目：编号 → 代码。 */
    private static final Map<String, String> LEVEL_ONE = Map.ofEntries(Map.entry("1001", "CASH_AND_BANK"),
        Map.entry("1101", "CHANNEL_RECEIVABLES"), Map.entry("1131", "OTHER_RECEIVABLES"),
        Map.entry("1171", "TAX_RECEIVABLES"), Map.entry("1901", "SUSPENSE_ASSETS"),
        Map.entry("2201", "CUSTOMER_FUNDS_PAYABLE"), Map.entry("2211", "CUSTOMER_DEPOSITS"),
        Map.entry("2221", "TAXES_PAYABLE"), Map.entry("2231", "CHANNEL_PAYABLES"), Map.entry("2241", "ACCRUED_EXPENSES"),
        Map.entry("2251", "OTHER_PAYABLES"), Map.entry("2901", "SUSPENSE_LIABILITIES"), Map.entry("3001", "SHARE_CAPITAL"),
        Map.entry("3101", "RETAINED_EARNINGS_TOTAL"), Map.entry("3103", "PROFIT_FOR_PERIOD"),
        Map.entry("3201", "DISTRIBUTIONS"), Map.entry("4001", "OPERATING_REVENUE"), Map.entry("5001", "COST_OF_SERVICES"),
        Map.entry("5101", "OTHER_LOSSES"));

    /** 一级首位与会计要素。 */
    private static final Map<Character, AccountClass> CLASS_DIGITS = Map.of('1', AccountClass.ASSET,
        '2', AccountClass.LIABILITY, '3', AccountClass.EQUITY, '4', AccountClass.REVENUE, '5', AccountClass.EXPENSE);

    /** 二级汇总科目数量（ID 3201—3226）。 */
    private static final int LEVEL_TWO_COUNT = 26;

    /** 三级末级科目数量（29 个既有科目）。 */
    private static final int LEAF_COUNT = 29;

    @Test
    void test_standardChartIsCompleteThreeLevelTree() {
        List<JsonNode> accounts = accounts();
        Map<String, JsonNode> byId = accounts.stream()
            .collect(Collectors.toMap(item -> item.path("chartAccountId").asString(), Function.identity()));
        Map<Integer, List<JsonNode>> byLevel = accounts.stream()
            .collect(Collectors.groupingBy(item -> item.path("level").asInt()));
        List<JsonNode> levelOne = byLevel.get(1);
        List<JsonNode> leaves = accounts.stream().filter(item -> item.path("postingAllowed").asBoolean()).toList();

        assertEquals(Set.of(1, 2, 3), byLevel.keySet());
        assertEquals(LEVEL_ONE.size() + LEVEL_TWO_COUNT + LEAF_COUNT, accounts.size());
        assertEquals(LEVEL_TWO_COUNT, byLevel.get(2).size());
        assertEquals(byLevel.get(3), leaves, "三级科目即全部可记账末级科目");
        assertEquals(LEVEL_ONE, levelOne.stream().collect(Collectors.toMap(item -> item.path("accountNo").asString(),
            item -> item.path("accountCode").asString())));
        assertEquals(Set.copyOf(Arrays.asList(AccountClass.values())), levelOne.stream()
            .map(item -> AccountClass.valueOf(item.path("accountClass").asString())).collect(Collectors.toSet()),
            "一级科目覆盖五类会计要素");
        accounts.forEach(item -> assertEquals(ChartAccountStatus.ACTIVE.getCode(), item.path("accountStatus").asString()));
        levelOne.forEach(this::assertLevelOne);
        byLevel.get(2)
            .forEach(summary -> assertChild(summary, byId.get(summary.path("parentChartAccountId").asString()), false));
        leaves.forEach(leaf -> assertChild(leaf, byId.get(leaf.path("parentChartAccountId").asString()), true));
    }

    /** account 账户类型目录（OwnerAccountType）的科目编号与 STANDARD 科目表一致，且都是启用的末级科目。 */
    @Test
    void test_ownerAccountTypeNumbersMatchChart() {
        Map<String, JsonNode> byCode = accounts().stream()
            .collect(Collectors.toMap(item -> item.path("accountCode").asString(), Function.identity()));
        for (OwnerAccountType type : OwnerAccountType.values()) {
            JsonNode account = byCode.get(type.getCode());
            assertNotNull(account, "科目表缺少账户类型目录科目: " + type.getCode());
            assertAll(type.getCode(), () -> assertEquals(account.path("accountNo").asString(), type.getChartAccountNo()),
                () -> assertTrue(account.path("postingAllowed").asBoolean()),
                () -> assertEquals(type.getNormalSide().getCode(), account.path("normalSide").asString()));
        }
    }

    /** 一级汇总科目：4 位、无上级、不可记账、首位与类别一致；余额方向取类别默认方向，所有者分配例外为借。 */
    void assertLevelOne(JsonNode summary) {
        String accountNo = summary.path("accountNo").asString();
        AccountClass accountClass = AccountClass.valueOf(summary.path("accountClass").asString());
        NormalSide expectedSide = "3201".equals(accountNo) || accountClass == AccountClass.ASSET
            || accountClass == AccountClass.EXPENSE ? NormalSide.DEBIT : NormalSide.CREDIT;
        assertAll(accountNo, () -> assertEquals(4, accountNo.length()),
            () -> assertFalse(summary.path("postingAllowed").asBoolean()),
            () -> assertTrue(summary.path("parentChartAccountId").isNull()),
            () -> assertEquals(CLASS_DIGITS.get(accountNo.charAt(0)), accountClass),
            () -> assertEquals(expectedSide.getCode(), summary.path("normalSide").asString()));
    }

    /** 二级（汇总）或三级（末级）科目：上级为汇总科目且级次少 1，编号以上级编号开头并多 2 位，类别与余额方向同上级。 */
    void assertChild(JsonNode child, JsonNode parent, boolean leaf) {
        String accountNo = child.path("accountNo").asString();
        assertNotNull(parent, "二级、三级科目必须有上级: " + accountNo);
        String parentNo = parent.path("accountNo").asString();
        assertAll(accountNo, () -> assertEquals(leaf, child.path("postingAllowed").asBoolean()),
            () -> assertEquals(parent.path("level").asInt() + 1, child.path("level").asInt()),
            () -> assertEquals(parentNo.length() + 2, accountNo.length()),
            () -> assertTrue(accountNo.startsWith(parentNo)),
            () -> assertFalse(parent.path("postingAllowed").asBoolean()),
            () -> assertEquals(parent.path("accountClass").asString(), child.path("accountClass").asString()),
            () -> assertEquals(parent.path("normalSide").asString(), child.path("normalSide").asString()));
    }

    /** 读取 STANDARD 全部科目。 */
    List<JsonNode> accounts() {
        List<JsonNode> items = new ArrayList<>();
        get("/chart-of-accounts/v1/STANDARD/accounts").assertSuccess(200).json().path("items").forEach(items::add);
        return items;
    }
}
