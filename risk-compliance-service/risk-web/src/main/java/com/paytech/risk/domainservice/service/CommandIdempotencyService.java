package com.paytech.risk.domainservice.service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.domain.enums.CommandOperationType;

/**
 * 管理命令的幂等身份、重放和 Validate 恢复协议。
 */
public interface CommandIdempotencyService {

    /**
     * 使用已接收的 Operations Actor 构造幂等身份。
     *
     * @param actorId 正的 canonical signed BIGINT 文本
     * @param operationType 命令操作
     * @param idempotencyKey 原样的传输幂等键
     * @return 受格式校验的幂等身份
     */
    static CommandIdentity identity(String actorId, CommandOperationType operationType, String idempotencyKey) {
        if (!isPositiveSignedBigInt(actorId) || operationType == null || !isIdempotencyKey(idempotencyKey)) {
            throw new InvalidParameterException("幂等调用方、操作或命令标识格式无效");
        }
        return new CommandIdentity("OPERATIONS_ACCOUNT:" + actorId, operationType, idempotencyKey);
    }

    /**
     * 构造完成当前操作所允许的稳定首次响应引用。
     *
     * @param operationType 命令操作
     * @param resourceId 正的 canonical signed BIGINT 文本
     * @return 与操作相容的稳定响应引用
     */
    static ResponseReference responseReference(CommandOperationType operationType, String resourceId) {
        if (operationType == null || !isPositiveSignedBigInt(resourceId)) {
            throw new InvalidParameterException("响应资源标识格式无效");
        }
        return new ResponseReference(responseReferencePrefix(operationType) + resourceId);
    }

    /**
     * 对外部或持久化引用执行操作相容的严格解析。
     *
     * @param operationType 命令操作
     * @param value 待验证引用
     * @return 已验证的稳定响应引用
     */
    static ResponseReference parseResponseReference(CommandOperationType operationType, String value) {
        if (operationType == null || value == null) {
            throw new InvalidParameterException("响应资源引用格式无效");
        }
        String prefix = responseReferencePrefix(operationType);
        if (!value.startsWith(prefix) || !isPositiveSignedBigInt(value.substring(prefix.length()))) {
            throw new InvalidParameterException("响应资源引用格式无效");
        }
        return new ResponseReference(value);
    }

    /**
     * 对请求哈希和身份形成 Processor 可传递的输入。
     *
     * @param identity 精确幂等身份
     * @param schema 生成该 Hash 的闭合命令 schema
     * @param requestHash 64 位小写 SHA-256 文本
     * @return 已验证的请求
     */
    static CommandRequest request(CommandIdentity identity, String schema, String requestHash) {
        if (identity == null || !supportsSchema(identity.operationType(), schema) || !isRequestHash(requestHash)) {
            throw new InvalidParameterException("命令请求哈希格式无效");
        }
        return new CommandRequest(identity, schema, requestHash);
    }

    /**
     * 从完整命令身份生成仅供 Validate 状态变更使用的不可逆触发标识。
     *
     * @param request 已验证命令请求
     * @return 64 位小写 SHA-256；绝不返回原始幂等键
     */
    static String opaqueTriggerId(CommandRequest request) {
        if (request == null || request.identity() == null) {
            throw new InvalidParameterException("命令请求哈希格式无效");
        }
        String domain = request.identity().operationType() == CommandOperationType.DELETE_RULE_SET
            ? "RISK_DELETE_TRIGGER_V1" : "RISK_VALIDATE_TRIGGER_V1";
        String material = domain + "\u0000" + request.identity().callerScope() + "\u0000"
            + request.identity().operationType().getCode() + "\u0000" + request.identity().idempotencyKey();
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    /**
     * 在短写事务内声明或重放一个命令。
     *
     * @param request 已验证命令请求
     * @param firstNow 首次声明时写入的 UTC 时间
     * @return 首次、成功重放或 Validate 恢复标记
     */
    CommandClaim claim(CommandRequest request, Instant firstNow);

    /**
     * 仅在 Validate 的事务 A 中固定已创建 Candidate 的 Release 引用。
     *
     * @param request 已验证命令请求
     * @param releaseId 新建 Candidate 的正标识
     * @param now 当前 UTC 写入时间
     */
    void recordValidateProcessingReference(CommandRequest request, long releaseId, Instant now);

    /**
     * 将当前处理记录与不可变首次响应一同标为成功。
     *
     * @param request 已验证命令请求
     * @param responseReference 与操作相容的稳定响应引用
     * @param now 当前 UTC 写入时间
     */
    void complete(CommandRequest request, ResponseReference responseReference, Instant now);

    /**
     * 单一命令的精确数据库身份。
     *
     * @param callerScope actor 的 Operations 隔离范围
     * @param operationType 闭合命令类型
     * @param idempotencyKey 调用方幂等键
     */
    record CommandIdentity(String callerScope, CommandOperationType operationType, String idempotencyKey) {

        /**
         * 拒绝绕过 Actor 到 Scope 映射而直接伪造记录身份。
         */
        public CommandIdentity {
            if (!isCallerScope(callerScope) || operationType == null || !isIdempotencyKey(idempotencyKey)) {
                throw new InvalidParameterException("幂等调用方、操作或命令标识格式无效");
            }
        }
    }

    /**
     * 由对应 schema canonicalizer 给出的命令哈希。
     *
     * @param identity 精确幂等身份
     * @param schema 生成该 Hash 的闭合命令 schema
     * @param requestHash 64 位小写 SHA-256 文本
     */
    record CommandRequest(CommandIdentity identity, String schema, String requestHash) {

        /**
         * 拒绝没有精确身份或非 canonical SHA-256 的命令请求。
         */
        public CommandRequest {
            if (identity == null || !supportsSchema(identity.operationType(), schema) || !isRequestHash(requestHash)) {
                throw new InvalidParameterException("命令请求哈希格式无效");
            }
        }
    }

    /**
     * 稳定资源引用，而不是请求或响应 JSON。
     *
     * @param value 带类型 prefix 的稳定资源引用
     */
    record ResponseReference(String value) {

        /**
         * 不允许构造带 Payload、前导零或未知资源前缀的响应引用。
         */
        public ResponseReference {
            if (!isResponseReference(value)) {
                throw new InvalidParameterException("响应资源引用格式无效");
            }
        }
    }

    /** 命令声明的闭合结果。 */
    sealed interface CommandClaim permits First, SucceededReplay, ValidateProcessingRecovery {
    }

    /**
     * 当前短写事务新插入的处理记录。
     *
     * @param request 对应请求
     */
    record First(CommandRequest request) implements CommandClaim {

        /**
         * 首次声明必须携带已验证的原始请求。
         */
        public First {
            if (request == null) {
                throw new InvalidParameterException("命令请求哈希格式无效");
            }
        }
    }

    /**
     * 相同请求哈希的首次成功重放。
     *
     * @param responseReference 不可变首次响应引用
     */
    record SucceededReplay(ResponseReference responseReference) implements CommandClaim {

        /**
         * 成功重放只能锚定稳定资源引用。
         */
        public SucceededReplay {
            if (responseReference == null) {
                throw new InvalidParameterException("响应资源引用格式无效");
            }
        }
    }

    /**
     * Validate 的事务 A 已提交，但事务 B 尚未完成时的恢复入口。
     *
     * @param releaseId 已固定的 Candidate 标识
     */
    record ValidateProcessingRecovery(String releaseId) implements CommandClaim {

        /**
         * Validate 恢复只接受 canonical Candidate 标识。
         */
        public ValidateProcessingRecovery {
            if (!isPositiveSignedBigInt(releaseId)) {
                throw new InvalidParameterException("响应资源标识格式无效");
            }
        }
    }

    private static boolean isPositiveSignedBigInt(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isIdempotencyKey(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{8,128}");
    }

    private static boolean isCallerScope(String value) {
        if (value == null || !value.startsWith("OPERATIONS_ACCOUNT:")) {
            return false;
        }
        return isPositiveSignedBigInt(value.substring("OPERATIONS_ACCOUNT:".length()));
    }

    private static boolean isRequestHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean isResponseReference(String value) {
        if (value == null) {
            return false;
        }
        for (String prefix : java.util.List.of("SAVE_DECISION_FLOW:", "DELETE_DECISION_FLOW:")) {
            if (value.startsWith(prefix)) return isPositiveSignedBigInt(value.substring(prefix.length()));
        }
        if (value.startsWith("rule-set-deletion:")) {
            return isPositiveSignedBigInt(value.substring("rule-set-deletion:".length()));
        }
        if (value.startsWith("rule-set-version:")) {
            return isPositiveSignedBigInt(value.substring("rule-set-version:".length()));
        }
        if (value.startsWith("release:")) {
            return isPositiveSignedBigInt(value.substring("release:".length()));
        }
        return false;
    }

    private static String responseReferencePrefix(CommandOperationType operationType) {
        return switch (operationType) {
            case SAVE_DECISION_FLOW -> "SAVE_DECISION_FLOW:";
            case DELETE_DECISION_FLOW -> "DELETE_DECISION_FLOW:";
            case DELETE_RULE_SET -> "rule-set-deletion:";
            case CREATE_RULE_SET_VERSION, FREEZE_RULE_SET_VERSION -> "rule-set-version:";
            case CREATE_TEST_DECISION_RELEASE, VALIDATE_TEST_DECISION_RELEASE -> "release:";
        };
    }

    private static boolean supportsSchema(CommandOperationType operationType, String schema) {
        return expectedSchema(operationType).equals(schema) || (operationType == CommandOperationType.CREATE_RULE_SET_VERSION
            && ("RISK_CREATE_RULE_SET_CONFIGURATION_COMMAND_V1".equals(schema)
                || "RISK_CREATE_RULE_SET_VARIABLE_CONFIGURATION_COMMAND".equals(schema)));
    }

    private static String expectedSchema(CommandOperationType operationType) {
        return switch (operationType) {
            case SAVE_DECISION_FLOW -> "RISK_DECISION_FLOW_SAVE";
            case DELETE_DECISION_FLOW -> "RISK_DECISION_FLOW_DELETE";
            case DELETE_RULE_SET -> "RISK_DELETE_RULE_SET_COMMAND_V1";
            case CREATE_RULE_SET_VERSION -> "RISK_CREATE_RULE_SET_VERSION_COMMAND_V1";
            case FREEZE_RULE_SET_VERSION -> "RISK_FREEZE_RULE_SET_VERSION_COMMAND_V1";
            case CREATE_TEST_DECISION_RELEASE -> "RISK_CREATE_TEST_RELEASE_COMMAND_V1";
            case VALIDATE_TEST_DECISION_RELEASE -> "RISK_VALIDATE_TEST_RELEASE_COMMAND_V1";
        };
    }
}
