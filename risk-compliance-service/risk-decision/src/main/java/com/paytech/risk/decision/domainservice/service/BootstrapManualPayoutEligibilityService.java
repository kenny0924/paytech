package com.paytech.risk.decision.domainservice.service;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.ManualPayoutEligibilityCommand;
import com.paytech.risk.decision.domain.ManualPayoutEligibilityResult;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仅供本地契约联调的内存空控制集评估器。
 */
public final class BootstrapManualPayoutEligibilityService implements ManualPayoutEligibilityService {

    private static final BigInteger MAX_SIGNED_BIGINT = BigInteger.valueOf(Long.MAX_VALUE);
    private static final String EMPTY_CONTROL_SET_HASH = sha256("[]");
    private static final Map<String, String> SOURCE_TYPES = Map.of(
        "PAYOUT_CREATION", "PAYOUT",
        "PAYOUT_FINAL_APPROVAL", "PAYOUT",
        "PAYOUT_EXECUTION_CREATION", "PAYOUT_EXECUTION",
        "PAYOUT_EXTERNAL_SUBMISSION", "PAYOUT_EXECUTION");

    private final Map<String, StoredEvaluation> evaluations = new ConcurrentHashMap<>();

    @Override
    public ManualPayoutEligibilityResult evaluate(ManualPayoutEligibilityCommand command) {
        validate(command);
        String fingerprint = fingerprint(command);
        StoredEvaluation stored = evaluations.compute(command.commandKey(), (key, current) -> {
            if (current == null) {
                return new StoredEvaluation(fingerprint, createResult(command, fingerprint));
            }
            if (!current.fingerprint().equals(fingerprint)) {
                throw new ConcurrentRequestException("同一风控评估命令不能提交不同内容");
            }
            return current;
        });
        return stored.result();
    }

    private void validate(ManualPayoutEligibilityCommand command) {
        String expectedSourceType = SOURCE_TYPES.get(command.checkpointType());
        if (expectedSourceType == null || !expectedSourceType.equals(command.sourceFactType())) {
            throw new InvalidParameterException("出款检查点与来源事实类型不匹配");
        }
        boolean creation = command.checkpointType().equals("PAYOUT_CREATION")
            || command.checkpointType().equals("PAYOUT_EXECUTION_CREATION");
        if (creation && command.sourceFactVersion() != 1) {
            throw new InvalidParameterException("创建类检查点的来源事实版本必须为 1");
        }
        if (!command.roleType().equals("CLIENT") && !command.roleType().equals("LOCAL_PARTNER")) {
            throw new InvalidParameterException("出款角色只允许 CLIENT 或 LOCAL_PARTNER");
        }
        validateId(command.roleId(), "角色标识");
        validateId(command.operatingEntityId(), "经营主体标识");
        validateId(command.sourceFactId(), "来源事实标识");
        if (!sha256(canonicalInput(command)).equals(command.inputHash())) {
            throw new InvalidParameterException("出款评估 input_hash 与标准输入不一致");
        }
    }

    private void validateId(String value, String label) {
        if (new BigInteger(value).compareTo(MAX_SIGNED_BIGINT) > 0) {
            throw new InvalidParameterException(label + "超出 signed BIGINT 范围");
        }
    }

    private ManualPayoutEligibilityResult createResult(ManualPayoutEligibilityCommand command,
        String fingerprint) {
        long assessmentId = positiveLong(sha256("assessment:" + command.commandKey() + ":" + fingerprint));
        long evaluationId = positiveLong(sha256("evaluation:" + command.commandKey() + ":" + fingerprint));
        String amount = command.roleType().equals("CLIENT") ? "0" : null;
        String currency = command.roleType().equals("CLIENT") ? command.currency() : null;
        return new ManualPayoutEligibilityResult(assessmentId, evaluationId, "ALLOW", List.of(), List.of(),
            amount, currency, EMPTY_CONTROL_SET_HASH);
    }

    private String fingerprint(ManualPayoutEligibilityCommand command) {
        return sha256(canonicalInput(command));
    }

    private String canonicalInput(ManualPayoutEligibilityCommand command) {
        return "{\"bankCountry\":\"" + command.bankCountry() + "\",\"checkpointType\":\""
            + command.checkpointType() + "\",\"currency\":\"" + command.currency()
            + "\",\"grossAmount\":\"" + command.grossAmount().toPlainString()
            + "\",\"operatingEntityId\":\"" + command.operatingEntityId() + "\",\"roleId\":\""
            + command.roleId() + "\",\"roleType\":\"" + command.roleType()
            + "\",\"schema\":\"CODE-MOR:RISK:SETTLEMENT_ELIGIBILITY_INPUT:1\",\"sourceFactId\":\""
            + command.sourceFactId() + "\",\"sourceFactType\":\"" + command.sourceFactType()
            + "\",\"sourceFactVersion\":" + command.sourceFactVersion() + "}";
    }

    private static long positiveLong(String hash) {
        return Long.parseUnsignedLong(hash.substring(0, 15), 16);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private record StoredEvaluation(String fingerprint, ManualPayoutEligibilityResult result) {
    }
}
