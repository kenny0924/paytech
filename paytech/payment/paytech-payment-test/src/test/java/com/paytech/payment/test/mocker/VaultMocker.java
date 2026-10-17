package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.card.integration.impl.CardKeyClientImpl;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealResponse;

/**
 * 卡 DEK 的合成 Vault DTO（第 12 步前卡域仍在 payment 进程内）：任何未配置或用途不符的请求都使测试失败。
 *
 * <p>
 * 卡 DEK 按 Vault REFERENCE 语义托管：同一幂等键返回原 Token，Reveal 返回原值；只接受 Payment（payment-service）以约定数据类型与用途调用。
 * 渠道账号的 CHANNEL_EXECUTION 读取已随渠道配置迁到 channel-connector，Payment 收到即为意外调用。
 */
@TestComponent
public class VaultMocker implements Clear {
    private static final String CALLER = "payment-service";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, String> cardKeys = new HashMap<>();
    private final Map<String, String> cardKeyTokens = new HashMap<>();
    private final List<CardKeyProtect> cardKeyProtects = new ArrayList<>();
    private final List<String> cardKeyReveals = new ArrayList<>();
    private MockHttpFault cardKeyProtectFault;
    private final List<String> unexpected = new ArrayList<>();

    /** HTTP Mock 实际调用此处，测试只记录合成 Token，不记录 Payload。 */
    public synchronized RevealResponse reveal(String caller, String token, String purpose) {
        if (cardKeys.containsKey(token)) {
            return revealCardKey(caller, token, purpose);
        }
        unexpected.add("unconfigured-reveal");
        throw new AssertionError("Vault 请求未配置或调用者/用途不符");
    }

    /**
     * HTTP Mock 实际调用此处：只接受卡 DEK 的 Protect，同一幂等键返回原 Token
     * <li>记录数据类型、模式、用途、幂等键与值长度，不记录 DEK。</li>
     */
    public synchronized ProtectResponse protect(String caller, String idempotencyKey, ProtectRequest request) {
        if (!CALLER.equals(caller) || !CardKeyClientImpl.DATA_TYPE.equals(request.dataType())
            || !TokenMode.REFERENCE.getCode().equals(request.tokenMode())
            || !CardKeyClientImpl.PROTECT_PURPOSE.equals(request.purpose())) {
            unexpected.add("protect");
            throw new AssertionError("Vault Protect 的调用者、数据类型、模式或用途不符");
        }
        cardKeyProtects.add(new CardKeyProtect(idempotencyKey, Base64.getDecoder().decode(request.value()).length));
        if (cardKeyProtectFault != null) {
            throw cardKeyProtectFault;
        }
        String token = cardKeyTokens.computeIfAbsent(idempotencyKey, key -> newToken());
        cardKeys.put(token, request.value());
        return new ProtectResponse(token);
    }

    /** 卡 DEK 的 Reveal 只接受读卡用途。 */
    RevealResponse revealCardKey(String caller, String token, String purpose) {
        if (!CALLER.equals(caller) || !CardKeyClientImpl.REVEAL_PURPOSE.equals(purpose)) {
            unexpected.add("card-key-reveal");
            throw new AssertionError("卡 DEK Reveal 的调用者或用途不符");
        }
        cardKeyReveals.add(token);
        return new RevealResponse(cardKeys.get(token));
    }

    /** 使卡 DEK 的 Protect 返回指定故障，验证申请失败关闭。 */
    public synchronized void failCardKeyProtect(MockHttpFault fault) {
        cardKeyProtectFault = fault;
    }

    /** 已收到的卡 DEK Protect（不含 DEK）。 */
    public synchronized List<CardKeyProtect> cardKeyProtects() {
        return List.copyOf(cardKeyProtects);
    }

    /** 已收到的卡 DEK Reveal 次数。 */
    public synchronized int cardKeyRevealCount() {
        return cardKeyReveals.size();
    }

    /** 43 位 URL 安全 Base64 的合成 Token。 */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 每例必须从完全空的 Mock 状态开始。 */
    public synchronized void assertEmpty() {
        assertTrue(unexpected.isEmpty() && cardKeys.isEmpty() && cardKeyTokens.isEmpty() && cardKeyProtects.isEmpty()
            && cardKeyReveals.isEmpty() && cardKeyProtectFault == null);
    }

    /** 即使业务捕获 HTTP 故障，也不能掩盖意外调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertTrue(unexpected.isEmpty(), "存在意外 Vault 操作");
    }

    /** 由 Foundation 的 AfterEach 统一回收响应与调用记录。 */
    @Override
    public synchronized void clear() {
        unexpected.clear();
        cardKeys.clear();
        cardKeyTokens.clear();
        cardKeyProtects.clear();
        cardKeyReveals.clear();
        cardKeyProtectFault = null;
    }

    /**
     * 一次卡 DEK Protect
     *
     * @param idempotencyKey 幂等键
     * @param dataKeyBytes DEK 解码后的字节数
     */
    public record CardKeyProtect(String idempotencyKey, int dataKeyBytes) {}
}
