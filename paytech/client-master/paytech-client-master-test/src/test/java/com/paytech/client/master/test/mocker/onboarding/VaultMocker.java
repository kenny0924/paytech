package com.paytech.client.master.test.mocker.onboarding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealRequest;
import com.paytech.vault.client.internal.wire.RevealResponse;

/**
 * Vault Protect 的测试 DTO 配置与调用记录
 * <li>STABLE Token 为 数据类型:值 的 SHA-256（base64url，43 字符），同值同 Token，与真实 Vault 的稳定语义一致。</li>
 * <li>只记录数据类型、模式与用途，不保存号码原文；未启用时 Protect 记为意外调用。</li>
 * <li>Reveal 只返回本例配置的测试值（联系方式），未配置的 Token 记为意外调用；证件 Token 从不 Reveal。</li>
 */
@TestComponent
public class VaultMocker implements Clear {
    /** 一次 Protect 的非敏感记录。 */
    public record ProtectCall(String caller, String dataType, String tokenMode, String purpose) {
    }

    private final List<ProtectCall> calls = new CopyOnWriteArrayList<>();
    private final java.util.Map<String, String> revealable = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<String> revealPurposes = new CopyOnWriteArrayList<>();
    private volatile boolean enabled;
    private volatile boolean unavailable;

    /** 本例允许 Protect。 */
    public void mockProtect() {
        enabled = true;
        unavailable = false;
    }

    /** 本例 Protect 断连。 */
    public void mockUnavailable() {
        enabled = true;
        unavailable = true;
    }

    /** 本例允许 Reveal 某个 Token，返回指定的测试值。 */
    public void mockReveal(String token, String value) {
        revealable.put(token, value);
    }

    /** Reveal：未配置的 Token 返回 null（意外调用）；只记录用途。 */
    public RevealResponse reveal(RevealRequest request) {
        String value = revealable.get(request.token());
        if (value == null) {
            return null;
        }
        revealPurposes.add(request.purpose());
        return new RevealResponse(value);
    }

    /** Reveal 用途记录快照。 */
    public List<String> revealPurposes() {
        return new ArrayList<>(revealPurposes);
    }

    /** 与 Mock 一致的稳定 Token，用于构造已占用的身份键。 */
    public static String token(String dataType, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((dataType + ":" + value).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Protect：未启用返回 null（意外调用），断连抛出故障。 */
    public ProtectResponse protect(String caller, ProtectRequest request) {
        if (!enabled) {
            return null;
        }
        calls.add(new ProtectCall(caller, request.dataType(), request.tokenMode(), request.purpose()));
        if (unavailable) {
            throw MockHttpFault.disconnect();
        }
        return new ProtectResponse(token(request.dataType(), request.value()));
    }

    /** Protect 记录快照。 */
    public List<ProtectCall> calls() {
        return new ArrayList<>(calls);
    }

    /** 某数据类型的 Protect 次数。 */
    public long count(String dataType) {
        return calls.stream().filter(call -> call.dataType().equals(dataType)).count();
    }

    /** 每例清空。 */
    @Override
    public void clear() {
        calls.clear();
        revealable.clear();
        revealPurposes.clear();
        enabled = false;
        unavailable = false;
    }
}
