package com.paytech.channel.connector.auto.channel;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.channel.connector.api.channel.ChannelAccountApi;
import com.paytech.channel.connector.api.channel.request.ChangeChannelStatusRequestBody;
import com.paytech.channel.connector.api.channel.request.ConfigureChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.CreateChannelAccountRequestBody;
import com.paytech.channel.connector.api.channel.request.UpdateChannelAccountRequestBody;
import com.paytech.channel.connector.core.dal.dataobject.ChannelAccount;
import com.paytech.channel.connector.core.dal.dataobject.PaymentChannel;
import com.paytech.channel.connector.core.enums.ChangeTargetType;
import com.paytech.channel.connector.core.enums.ChangeType;
import com.paytech.channel.connector.core.enums.ChannelStatus;
import com.paytech.channel.connector.domain.ChannelAccountConfigPayload;
import com.paytech.channel.connector.test.base.AbstractChannelConfigApiTest;
import com.paytech.channel.connector.test.builder.ChannelConfigRequestBuilder;
import com.paytech.channel.connector.test.checker.ChannelCostResponseChecker;
import com.paytech.channel.connector.test.mocker.VaultMocker.ProtectCall;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClientErrorCode;

/**
 * 渠道技术账号管理：创建、OE 格式、配置写入（Vault Protect 幂等键、Token 落库、变更记录、零泄漏）、重复配置、Vault 拒绝、
 * 激活与关闭前置、改名与列表、同账号并发配置。
 *
 * @see ChannelAccountApi#createChannelAccount(String, CreateChannelAccountRequestBody)
 * @see ChannelAccountApi#listChannelAccounts(String, String, String, Integer, Integer)
 * @see ChannelAccountApi#getChannelAccount(String)
 * @see ChannelAccountApi#updateChannelAccount(String, String, UpdateChannelAccountRequestBody)
 * @see ChannelAccountApi#configureChannelAccount(String, String, ConfigureChannelAccountRequestBody)
 * @see ChannelAccountApi#changeChannelAccountStatus(String, String, ChangeChannelStatusRequestBody)
 */
class ChannelAccountApiTest extends AbstractChannelConfigApiTest {
    /** 本类公共 ACTIVE 渠道。 */
    private PaymentChannel channel;

    /** 本类公共：一个 ACTIVE 的 NI 渠道。 */
    @BeforeEach
    void setUpClassMocks() {
        channel = data.channel("NI", ChannelStatus.ACTIVE);
    }

    /** 创建 DRAFT 账号，无配置；同事务写入 CREATE 变更记录。 */
    @Test
    void test_createAccount() {
        ChannelCostResponseChecker created = createAccount().assertSuccess(201)
            .assertText("status", ChannelStatus.DRAFT.getCode()).assertText("entityNo", ENTITY_NO);

        long accountId = Long.parseLong(created.text("channelAccountId"));
        assertAll(() -> assertFalse(created.json().path("configured").asBoolean()),
            () -> assertNull(created.text("vaultTokenSuffix")),
            () -> assertNull(testMapper.selectAccount(accountId).get("account_vault_token")),
            () -> assertChange(changes(ChangeTargetType.CHANNEL_ACCOUNT, accountId).getFirst(),
                ChangeType.CREATE.getCode(), null, null));
    }

    /** OE 格式错误 422，渠道不存在 400；均不写库。 */
    @Test
    void test_invalidParameter() {
        assertValidationError(command(HttpMethod.POST, ACCOUNT_PATH,
            ChannelConfigRequestBuilder.account(channel.getChannelId(), "00001")));
        command(HttpMethod.POST, ACCOUNT_PATH, ChannelConfigRequestBuilder.account(1L, ENTITY_NO))
            .assertBusinessRejected("渠道不存在: 1");
        assertValidationError(command(HttpMethod.PUT, configurationPath(1L),
            ChannelConfigRequestBuilder.configuration(Map.of("1bad", "x"), Map.of())));
        assertEquals(1, testMapper.countRows());
    }

    /** 配置写入：Protect 的数据类型 / 模式 / 用途 / 幂等键与原值摘要一致，Token 落库，响应与变更记录不含凭据。 */
    @Test
    void test_configureAccount() {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        expectConfigurationProtect(TOKEN_A);

        ChannelCostResponseChecker configured = configure(account).assertSuccess(200);

        ProtectCall call = vault.protectCalls().getFirst();
        assertAll(() -> assertTrue(configured.json().path("configured").asBoolean()),
            () -> assertEquals(TOKEN_A.substring(39), configured.text("vaultTokenSuffix")),
            () -> assertFalse(configured.json().toString().contains("synthetic-api-key-value")),
            () -> assertFalse(configured.json().toString().contains(TOKEN_A)),
            () -> assertEquals(TOKEN_A, testMapper.selectAccount(account.getChannelAccountId()).get("account_vault_token")),
            () -> assertEquals(1, vault.protectCalls().size()),
            () -> assertEquals(TokenMode.REFERENCE.getCode(), call.tokenMode()),
            () -> assertEquals("channel-account-config." + account.getChannelAccountId() + "." + call.valueSha256(),
                call.idempotencyKey()),
            () -> assertEquals(Map.of("schemaVersion", 1, "channelCode", "NI", "channelAccountId",
                account.getChannelAccountId().toString(), "entityNo", ENTITY_NO, "parameterKeys",
                new TreeSet<>(Set.of("outletId")), "credentialKeys", new TreeSet<>(Set.of("apiKey"))), call.structure()));
        var change = changes(ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId()).getFirst();
        assertChange(change, ChangeType.CONFIGURATION.getCode(), null, null);
        assertNull(change.get("reason"));
        transactions.assertOutsideTransactions();
    }

    /** 同内容重复配置复用同一幂等键并得到同一 Token；内容变化产生新幂等键与新 Token。 */
    @Test
    void test_reconfigureAccount() {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        expectConfigurationProtect(TOKEN_A, TOKEN_B);

        configure(account).assertSuccess(200);
        configure(account).assertSuccess(200);
        command(HttpMethod.PUT, configurationPath(account.getChannelAccountId()), ChannelConfigRequestBuilder
            .configuration(Map.of("outletId", "other-outlet"), ChannelConfigRequestBuilder.CREDENTIALS))
            .assertSuccess(200);

        List<ProtectCall> calls = vault.protectCalls();
        assertAll(() -> assertEquals(3, calls.size()),
            () -> assertEquals(calls.get(0).idempotencyKey(), calls.get(1).idempotencyKey()),
            () -> assertFalse(calls.get(0).idempotencyKey().equals(calls.get(2).idempotencyKey())),
            () -> assertEquals(TOKEN_B, testMapper.selectAccount(account.getChannelAccountId()).get("account_vault_token")),
            () -> assertEquals(3, changes(ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId()).size()));
    }

    /** Vault 明确拒绝时原样失败，不写 Token 与变更记录。 */
    @Test
    void test_vaultRejectsProtect() {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        expectConfigurationProtect(TOKEN_A);
        vault.protectProblem(403, new ApiProblemResponse("about:blank", "Forbidden", "访问被拒绝", VaultClientErrorCode.ACCESS_DENIED.getCode(),
            "channel-test-request", "2026-09-21T00:00:00.000000Z", null));

        int status = configure(account).status();

        assertAll(() -> assertTrue(status >= 400, "应失败: " + status),
            () -> assertNull(testMapper.selectAccount(account.getChannelAccountId()).get("account_vault_token")),
            () -> assertEquals(0, testMapper.countConfigChangeRows()),
            () -> assertEquals(1, vault.protectCalls().size()));
    }

    /** 已关闭账号拒绝配置写入，不调用 Vault。 */
    @Test
    void test_closedAccountRejectsConfiguration() {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.CLOSED);

        configure(account).assertBusinessRejected("已关闭，不能写入配置");

        assertEquals(List.of(), vault.protectCalls());
    }

    /** 未配置不能激活；写入配置后可激活；渠道非 ACTIVE 时同样不能激活。 */
    @Test
    void test_activateRequiresConfiguration() {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        expectConfigurationProtect(TOKEN_A);

        command(HttpMethod.POST, statusPath(account), status(ChannelStatus.ACTIVE))
            .assertBusinessRejected("已配置 false");
        configure(account).assertSuccess(200);
        command(HttpMethod.POST, statusPath(account), status(ChannelStatus.ACTIVE)).assertSuccess(200)
            .assertText("status", ChannelStatus.ACTIVE.getCode());

        PaymentChannel draft = data.channel("GEIDEA", ChannelStatus.DRAFT);
        ChannelAccount other = data.account(draft.getChannelId(), ENTITY_NO, TOKEN_B, ChannelStatus.DRAFT);
        command(HttpMethod.POST, statusPath(other), status(ChannelStatus.ACTIVE)).assertBusinessRejected("渠道状态 DRAFT");
        assertChange(changes(ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId()).get(1),
            ChangeType.STATUS.getCode(), ChannelStatus.DRAFT.getCode(), ChannelStatus.ACTIVE.getCode());
    }

    /** 有未关闭 MID 绑定时拒绝关闭账号；MID 关闭后可关闭。 */
    @Test
    void test_closeBlockedByBoundMid() {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, TOKEN_A, ChannelStatus.ACTIVE);
        var mid = data.mid(channel.getChannelId(), account.getChannelAccountId(), ENTITY_NO, ChannelStatus.ACTIVE);
        get(ACCOUNT_PATH + "/" + account.getChannelAccountId()).assertSuccess(200);

        command(HttpMethod.POST, statusPath(account), status(ChannelStatus.CLOSED))
            .assertBusinessRejected("未关闭 MID 1");
        testMapper.rewriteMid(mid.getMidId(), ChannelStatus.CLOSED.getCode(), mid.getUpdateTime());
        command(HttpMethod.POST, statusPath(account), status(ChannelStatus.CLOSED)).assertSuccess(200)
            .assertText("status", ChannelStatus.CLOSED.getCode());
    }

    /** 详情只返回配置标志与 Token 末 4 位与绑定 MID 数，不返回 Token、参数或凭据。 */
    @Test
    void test_detailExcludesCredentials() {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, TOKEN_A, ChannelStatus.ACTIVE);
        data.mid(channel.getChannelId(), account.getChannelAccountId(), ENTITY_NO, ChannelStatus.DRAFT);

        var detail = get(ACCOUNT_PATH + "/" + account.getChannelAccountId()).assertSuccess(200).json();

        assertAll(() -> assertTrue(detail.path("configured").asBoolean()),
            () -> assertEquals("1a2B", detail.path("vaultTokenSuffix").asString()),
            () -> assertEquals(1, detail.path("boundMidCount").asInt()),
            () -> assertFalse(detail.toString().contains(TOKEN_A)),
            () -> assertTrue(detail.path("accountVaultToken").isMissingNode()),
            () -> assertTrue(detail.path("credentials").isMissingNode()));
    }

    /** 改名与备注写入 UPDATE 记录；列表按渠道、OE 与状态筛选。 */
    @Test
    void test_updateAndList() {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        data.account(channel.getChannelId(), "SA001", TOKEN_A, ChannelStatus.ACTIVE);
        UpdateChannelAccountRequestBody body = new UpdateChannelAccountRequestBody();
        body.setAccountName("新账号名");

        command(HttpMethod.PUT, ACCOUNT_PATH + "/" + account.getChannelAccountId(), body).assertSuccess(200)
            .assertText("accountName", "新账号名");

        assertAll(() -> assertNull(testMapper.selectAccount(account.getChannelAccountId()).get("remark")),
            () -> assertChange(changes(ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId()).getFirst(),
                ChangeType.UPDATE.getCode(), null, null));
        get(ACCOUNT_PATH + "?channelId=" + channel.getChannelId()).assertPage(1, 10, 2, 2);
        get(ACCOUNT_PATH + "?entityNo=SA001").assertPage(1, 10, 1, 1);
        var drafts = get(ACCOUNT_PATH + "?status=DRAFT").assertPage(1, 10, 1, 1).item(0);
        assertEquals(account.getChannelAccountId().toString(), drafts.path("channelAccountId").asString());
    }

    /** 同账号两线程并发写入同内容：资源锁串行，两次 Protect 使用同一幂等键，最终一个 Token、两条变更记录。 */
    @Test
    void test_concurrentConfiguration() throws Exception {
        ChannelAccount account = data.account(channel.getChannelId(), ENTITY_NO, null, ChannelStatus.DRAFT);
        expectConfigurationProtect(TOKEN_A);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task = () -> {
                start.await();
                return configure(account).status();
            };
            Future<Integer> first = executor.submit(task);
            Future<Integer> second = executor.submit(task);
            start.countDown();
            assertEquals(List.of(200, 200), List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
        } finally {
            executor.shutdownNow();
        }

        List<ProtectCall> calls = vault.protectCalls();
        assertAll(() -> assertEquals(2, calls.size()),
            () -> assertEquals(calls.get(0).idempotencyKey(), calls.get(1).idempotencyKey()),
            () -> assertEquals(TOKEN_A, testMapper.selectAccount(account.getChannelAccountId()).get("account_vault_token")),
            () -> assertEquals(2, changes(ChangeTargetType.CHANNEL_ACCOUNT, account.getChannelAccountId()).size()));
    }

    /** 以本类渠道创建标准账号。 */
    ChannelCostResponseChecker createAccount() {
        return command(HttpMethod.POST, ACCOUNT_PATH, ChannelConfigRequestBuilder.account(channel.getChannelId(),
            ENTITY_NO));
    }

    /** 以标准参数与凭据写入配置。 */
    ChannelCostResponseChecker configure(ChannelAccount account) {
        return command(HttpMethod.PUT, configurationPath(account.getChannelAccountId()), ChannelConfigRequestBuilder
            .configuration(ChannelConfigRequestBuilder.PARAMETERS, ChannelConfigRequestBuilder.CREDENTIALS));
    }

    /** 准备账号配置写入的 Protect 预期。 */
    void expectConfigurationProtect(String... tokens) {
        vault.expectProtect(ChannelAccountConfigPayload.DATA_TYPE, ChannelAccountConfigPayload.PURPOSE, tokens);
    }

    /** 配置写入路径。 */
    static String configurationPath(long accountId) {
        return ACCOUNT_PATH + "/" + accountId + "/configuration";
    }

    /** 状态路径。 */
    static String statusPath(ChannelAccount account) {
        return ACCOUNT_PATH + "/" + account.getChannelAccountId() + "/status";
    }
}
