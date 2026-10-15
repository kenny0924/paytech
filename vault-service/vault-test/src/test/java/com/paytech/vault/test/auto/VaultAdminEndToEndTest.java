package com.paytech.vault.test.auto;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.FailException;
import com.paytech.vault.client.ProtectResult;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.admin.VaultAdminClient.ManualEventFilter;
import com.paytech.vault.client.admin.request.CreateAccessRuleRequestBody;
import com.paytech.vault.client.admin.request.CreateDataTypeRequestBody;
import com.paytech.vault.client.admin.request.ManualProtectRequestBody;
import com.paytech.vault.client.admin.request.ManualRevealRequestBody;
import com.paytech.vault.client.admin.request.UpdateDataTypeRequestBody;
import com.paytech.vault.test.support.GoVaultProcess;
import com.paytech.vault.test.support.VaultDatabaseFixture;
import com.paytech.vault.test.support.VaultResponseChecker;

/** 真实 Go Vault + 真实 VaultAdminClient：策略维护热加载生效、人工加/解密、审计查询与版本冲突。 */
class VaultAdminEndToEndTest {

    @Test
    void test_adminMaintainsPolicyAndManualAccess() throws Exception {
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
            String dataType = database.type("ADMIN_VALUE");
            server.withAdminClient(admin -> {
                var created = database.call(() -> admin.createDataType(database.operator(), createType(dataType)));
                assertEquals(1, created.getDataType().getVersion());
                database.call(() -> admin.createAccessRule(database.operator(), protectRule(database.caller("a"), dataType)));
            });

            // 新规则在下一次刷新后生效：调用方 a 可以按 REFERENCE 模式加密。
            server.withClient("a", client -> await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100))
                .ignoreException(FailException.class).untilAsserted(() -> database.remember(database.call(() -> client.protect(
                    database.command("rule-" + System.nanoTime(), dataType, TokenMode.REFERENCE, database.canary()))))));

            server.withAdminClient(admin -> {
                String submission = database.key("manual-protect");
                String token = database.call(() -> admin.manualProtect(database.operator(), submission, manualProtect(dataType, database.canary()))).getToken();
                database.remember(new ProtectResult(token));
                assertEquals(token, database.call(() -> admin.manualProtect(database.operator(), submission,
                    manualProtect(dataType, database.canary()))).getToken(), "同一提交重放必须返回同一 Token");

                var revealed = database.call(() -> admin.manualReveal(database.operator(), manualReveal(token)));
                assertEquals(dataType, revealed.getDataTypeCode());
                VaultResponseChecker.value(database.canary(), new com.paytech.vault.client.RevealResult(revealed.getValue()));

                var events = database.call(() -> admin.listManualEvents(database.operator(),
                    new ManualEventFilter(database.operator(), "REVEAL", null, null, null), null, null));
                assertEquals(1, events.total());
                assertEquals("OPS-E2E", events.items().getFirst().getTicketRef());

                var changes = database.call(() -> admin.listPolicyChanges(database.operator(), "DATA_TYPE", dataType, null, null));
                assertEquals(1, changes.total());
                var stale = new UpdateDataTypeRequestBody();
                stale.setExpectedVersion(9);
                stale.setDataClass("STANDARD");
                stale.setDescription("stale");
                stale.setManualProtectEnabled(true);
                stale.setManualRevealEnabled(true);
                stale.setStatus("ACTIVE");
                stale.setReason("e2e");
                VaultResponseChecker.failure("POLICY_VERSION_CONFLICT",
                    () -> database.call(() -> admin.updateDataType(database.operator(), dataType, stale)));
            });
        }
    }

    @Test
    void test_valueFormatRoundTripAndManualProtectValidation() throws Exception {
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
            String dataType = database.type("ADMIN_PHONE");
            server.withAdminClient(admin -> {
                var body = createType(dataType);
                body.setValueFormat("REGEX");
                body.setValuePattern("\\+[1-9][0-9]{7,14}");
                body.setSampleValues(List.of("+971500000001"));
                var created = database.call(() -> admin.createDataType(database.operator(), body));
                assertEquals("REGEX", created.getDataType().getValueFormat());
                assertEquals("\\+[1-9][0-9]{7,14}", created.getDataType().getValuePattern());

                // 客户端对未设置的 valueFormat/valuePattern/sampleValues 发送 null：Vault 视为未传，保持原格式。
                var legacy = new UpdateDataTypeRequestBody();
                legacy.setExpectedVersion(1);
                legacy.setDataClass("STANDARD");
                legacy.setDescription("vault-test admin legacy");
                legacy.setManualProtectEnabled(true);
                legacy.setManualRevealEnabled(true);
                legacy.setStatus("ACTIVE");
                legacy.setReason("e2e");
                var kept = database.call(() -> admin.updateDataType(database.operator(), dataType, legacy));
                assertEquals("REGEX", kept.getDataType().getValueFormat());
                assertEquals("\\+[1-9][0-9]{7,14}", kept.getDataType().getValuePattern());

                var switched = new UpdateDataTypeRequestBody();
                switched.setExpectedVersion(2);
                switched.setDataClass("STANDARD");
                switched.setDescription("vault-test admin");
                switched.setManualProtectEnabled(true);
                switched.setManualRevealEnabled(true);
                switched.setStatus("ACTIVE");
                switched.setReason("e2e");
                switched.setValueFormat("REGEX");
                switched.setValuePattern("[0-9]{4}");
                VaultResponseChecker.failure("SAMPLE_VALUE_INVALID",
                    () -> database.call(() -> admin.updateDataType(database.operator(), dataType, switched)));
            });

            // 新类型在下一次刷新后生效；不符合正则的人工加密被拒绝，符合的成功。
            server.withAdminClient(admin -> await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100))
                .ignoreException(FailException.class).untilAsserted(() -> database.remember(new ProtectResult(database.call(() -> admin.manualProtect(
                    database.operator(), database.key("format-ok"), manualProtect(dataType, "+971500000002"))).getToken()))));
            server.withAdminClient(admin -> VaultResponseChecker.failure("VALUE_PATTERN_MISMATCH",
                () -> database.call(() -> admin.manualProtect(database.operator(), database.key("format-bad"), manualProtect(dataType, "971500000002")))));
        }
    }

    private static CreateDataTypeRequestBody createType(String code) {
        var body = new CreateDataTypeRequestBody();
        body.setDataTypeCode(code);
        body.setTokenMode("REFERENCE");
        body.setDataClass("STANDARD");
        body.setDescription("vault-test admin");
        body.setManualProtectEnabled(true);
        body.setManualRevealEnabled(true);
        body.setReason("e2e");
        return body;
    }

    private static CreateAccessRuleRequestBody protectRule(String caller, String dataType) {
        var body = new CreateAccessRuleRequestBody();
        body.setCallerService(caller);
        body.setAction("PROTECT");
        body.setDataTypeCode(dataType);
        body.setPurposeCode("TEST");
        body.setReason("e2e");
        return body;
    }

    private static ManualProtectRequestBody manualProtect(String dataType, String value) {
        var body = new ManualProtectRequestBody();
        body.setDataTypeCode(dataType);
        body.setValue(value);
        body.setReasonCode("DATA_REPAIR");
        body.setTicketRef("OPS-E2E");
        body.setAllowedDataClasses(List.of("STANDARD"));
        return body;
    }

    private static ManualRevealRequestBody manualReveal(String token) {
        var body = new ManualRevealRequestBody();
        body.setToken(token);
        body.setReasonCode("CUSTOMER_VERIFICATION");
        body.setTicketRef("OPS-E2E");
        body.setAllowedDataClasses(List.of("STANDARD"));
        return body;
    }
}
