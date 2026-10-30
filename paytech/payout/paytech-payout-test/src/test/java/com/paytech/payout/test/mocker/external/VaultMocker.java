package com.paytech.payout.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealRequest;
import com.paytech.vault.client.internal.wire.RevealResponse;

/**
 * Vault Protect / Reveal 的 DTO Mock：只接受 payout-service 以 db/vault/dml.sql 登记的数据类型、模式与用途发起的调用
 * <li>Token 由（数据类型、模式、REFERENCE 的幂等键 / STABLE 的原值）确定性派生：同键重放同 Token、STABLE 同值同 Token，与 Vault 合同一致。</li>
 * <li>Reveal 需要先 Protect 过（只保留当前用例必须的最小状态）；调用记录只存原值 SHA-256，不存原文。</li>
 * <li>未接受的调用、调用者 / 类型 / 用途不符均记为意外调用，在 AfterEach 使测试失败。</li>
 */
@TestComponent
public class VaultMocker implements Clear {
    /** 期望的调用服务名。 */
    public static final String CALLER = "payout-service";

    /** 原值数据类型。 */
    public static final String DETAILS_TYPE = "PAYOUT_BENEFICIARY_V1";

    /** 查重键数据类型。 */
    public static final String ACCOUNT_KEY_TYPE = "PAYOUT_BANK_ACCOUNT_KEY_V1";

    /** Protect 用途。 */
    public static final String SUBMIT_PURPOSE = "PAYOUT_BENEFICIARY_SUBMIT";

    /** Reveal 用途。 */
    public static final String REVIEW_PURPOSE = "PAYOUT_BENEFICIARY_REVIEW";

    private final Map<String, String> protectedValues = new HashMap<>();

    private final List<ProtectCall> protects = new ArrayList<>();

    private final List<String> reveals = new ArrayList<>();

    private final List<String> unexpected = new ArrayList<>();

    private boolean protectAccepted;

    private boolean revealAccepted;

    private MockHttpFault protectFault;

    /** 本例接受 Protect。 */
    public synchronized void acceptProtect() {
        protectAccepted = true;
    }

    /** 本例接受 Reveal。 */
    public synchronized void acceptReveal() {
        revealAccepted = true;
    }

    /** 本例的 Protect 返回 HTTP 故障（仍记录调用）。 */
    public synchronized void failProtect(MockHttpFault fault) {
        protectAccepted = true;
        protectFault = fault;
    }

    /** Mock Controller 调用：核对并返回确定性 Token。 */
    public synchronized ProtectResponse protect(String caller, String idempotencyKey, ProtectRequest request) {
        boolean details = DETAILS_TYPE.equals(request.dataType()) && "REFERENCE".equals(request.tokenMode());
        boolean accountKey = ACCOUNT_KEY_TYPE.equals(request.dataType()) && "STABLE".equals(request.tokenMode());
        if (!protectAccepted || !CALLER.equals(caller) || !(details || accountKey) || !SUBMIT_PURPOSE.equals(request.purpose())) {
            unexpected.add("protect:" + request.dataType() + "|" + request.tokenMode() + "|" + request.purpose());
            throw new AssertionError("Vault Protect 未接受或调用者 / 数据类型 / 用途不符");
        }
        protects.add(new ProtectCall(idempotencyKey, request.dataType(), request.tokenMode(), sha256(request.value())));
        if (protectFault != null) {
            throw protectFault;
        }
        String token = token(request.dataType() + "|" + (details ? idempotencyKey : request.value()));
        protectedValues.put(token, request.value());
        return new ProtectResponse(token);
    }

    /** Mock Controller 调用：返回 Protect 过的原值。 */
    public synchronized RevealResponse reveal(String caller, RevealRequest request) {
        String value = protectedValues.get(request.token());
        if (!revealAccepted || !CALLER.equals(caller) || !REVIEW_PURPOSE.equals(request.purpose()) || value == null) {
            unexpected.add("reveal:" + request.purpose());
            throw new AssertionError("Vault Reveal 未接受或调用者 / 用途 / Token 不符");
        }
        reveals.add(request.token());
        return new RevealResponse(value);
    }

    /** 本例 Protect 调用记录。 */
    public synchronized List<ProtectCall> protectCalls() {
        return List.copyOf(protects);
    }

    /** 本例 Reveal 次数。 */
    public synchronized int revealCount() {
        return reveals.size();
    }

    /** 零调用断言。 */
    public synchronized void assertNoCalls() {
        assertTrue(protects.isEmpty() && reveals.isEmpty(), "不应调用 Vault");
    }

    /** 每例开始时必须为空基线。 */
    public synchronized void assertEmpty() {
        assertTrue(protectedValues.isEmpty() && protects.isEmpty() && reveals.isEmpty() && unexpected.isEmpty()
            && !protectAccepted && !revealAccepted && protectFault == null, "Vault Mock 应处于空基线");
    }

    /** 即使生产代码捕获了 Mock 错误，也由 AfterEach 让测试失败。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在意外 Vault 操作：" + unexpected);
    }

    /** 由 Foundation TestBase 在每例结束统一调用。 */
    @Override
    public synchronized void clear() {
        protectedValues.clear();
        protects.clear();
        reveals.clear();
        unexpected.clear();
        protectAccepted = false;
        revealAccepted = false;
        protectFault = null;
    }

    /** 43 位 Base64URL 的确定性 Token。 */
    static String token(String seed) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(seed));
    }

    /** 原值 SHA-256 hex。 */
    public static String sha256(String value) {
        return HexFormat.of().formatHex(digest(value));
    }

    /** SHA-256 字节。 */
    static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 一次 Protect 调用的稳定摘要；不保存原值
     *
     * @param idempotencyKey 幂等键
     * @param dataType 数据类型
     * @param tokenMode Token 模式
     * @param valueSha256 原值 SHA-256
     */
    public record ProtectCall(String idempotencyKey, String dataType, String tokenMode, String valueSha256) {
    }
}
