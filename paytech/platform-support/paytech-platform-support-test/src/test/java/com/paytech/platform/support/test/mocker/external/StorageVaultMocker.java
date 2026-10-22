package com.paytech.platform.support.test.mocker.external;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;

/**
 * 存储实例密钥的 Vault DTO：FILE_STORAGE_SETUP 的 Protect 与 FILE_STORAGE_ACCESS 的 Reveal
 * <li>只保存当前用例配置的合成或本机 MinIO 测试密钥；Reveal 按 Token 计数，供断言“更换后使用新 Token”和“停用后不读密钥”。</li>
 * <li>未配置的 Token 或 Protect 记为意外调用，由测试基类在清理前检查。</li>
 */
@TestComponent
public class StorageVaultMocker implements Clear {

    /** Vault Token 长度（Vault V1 合同：43 位 Base64URL）。 */
    private static final int TOKEN_LENGTH = 43;

    private final Map<String, String> credentials = new HashMap<>();
    private final Map<String, Integer> reveals = new HashMap<>();
    private final List<String> protectTokens = new ArrayList<>();
    private final List<String> protectedValues = new ArrayList<>();
    private final List<String> protectKeys = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();
    private boolean revealUnavailable;
    private Integer protectStatus;

    /** 由可读标签生成符合 Vault 合同的合成 Token：标签后补 0 到 43 位。 */
    public static String token(String label) {
        return (label + "0".repeat(TOKEN_LENGTH)).substring(0, TOKEN_LENGTH);
    }

    /** 配置 Token 对应的密钥对，Reveal 返回 FILE_STORAGE_CREDENTIAL_V1 JSON。 */
    public synchronized void credential(String token, String accessKeyId, String secretAccessKey) {
        credentials.put(token, "{\"accessKeyId\":\"" + accessKeyId + "\",\"secretAccessKey\":\"" + secretAccessKey
            + "\"}");
    }

    /** 配置 Token 返回任意原值，用于格式不合法的负例。 */
    public synchronized void rawCredential(String token, String value) {
        credentials.put(token, value);
    }

    /** 之后的 Reveal 返回 503，模拟 Vault 不可用。 */
    public synchronized void revealUnavailable() {
        revealUnavailable = true;
    }

    /** 下一次 Protect 返回该 Token；可多次调用按顺序返回。 */
    public synchronized void expectProtect(String token) {
        protectTokens.add(token);
        protectStatus = null;
    }

    /** 之后的 Protect 返回该 HTTP 状态，不计意外调用。 */
    public synchronized void protectFails(int status) {
        protectStatus = status;
    }

    /** Mock Controller 调用：返回配置的 Token 并记录原值与幂等键。 */
    public synchronized String protect(String key, String value) {
        if (protectStatus != null) {
            throw MockHttpFault.raw(protectStatus, "{}");
        }
        if (protectTokens.isEmpty()) {
            unexpected.add("protect");
            throw new IllegalStateException("未配置存储密钥 Protect");
        }
        protectedValues.add(value);
        protectKeys.add(key);
        return protectTokens.removeFirst();
    }

    /** Mock Controller 调用：返回配置的密钥原值。 */
    public synchronized String reveal(String token) {
        reveals.merge(token, 1, Integer::sum);
        if (revealUnavailable) {
            throw MockHttpFault.raw(503, "{}");
        }
        String value = credentials.get(token);
        if (value == null) {
            unexpected.add("reveal");
            throw new IllegalStateException("未配置存储密钥 Reveal");
        }
        return value;
    }

    /** 指定 Token 的 Reveal 次数。 */
    public synchronized int revealCount(String token) {
        return reveals.getOrDefault(token, 0);
    }

    /** 全部 Reveal 次数。 */
    public synchronized int revealCount() {
        return reveals.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** 已 Protect 的原值，按调用顺序。 */
    public synchronized List<String> protectedValues() {
        return List.copyOf(protectedValues);
    }

    /** Protect 使用的幂等键，按调用顺序。 */
    public synchronized List<String> protectKeys() {
        return List.copyOf(protectKeys);
    }

    /** 每例开始时为空基线。 */
    public synchronized void assertEmpty() {
        assertAll("存储 Vault Mock 应处于空基线", () -> assertTrue(credentials.isEmpty()),
            () -> assertTrue(reveals.isEmpty()), () -> assertTrue(protectTokens.isEmpty()),
            () -> assertTrue(unexpected.isEmpty()));
    }

    /** 清理前检查未配置的调用。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(List.of(), unexpected, "存在未配置的存储密钥 Vault 调用");
    }

    /** 每例清理配置与记录。 */
    @Override
    public synchronized void clear() {
        credentials.clear();
        reveals.clear();
        protectTokens.clear();
        protectedValues.clear();
        protectKeys.clear();
        unexpected.clear();
        revealUnavailable = false;
        protectStatus = null;
    }
}
