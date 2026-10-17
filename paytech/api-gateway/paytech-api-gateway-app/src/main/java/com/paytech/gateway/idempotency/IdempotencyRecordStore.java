package com.paytech.gateway.idempotency;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import reactor.core.publisher.Mono;

/**
 * 请求幂等记录存储。本期为 Redis，后续迁移到数据库时保持本接口与原子语义不变。
 */
public interface IdempotencyRecordStore {
    /**
     * 原子占位：不存在时写入 PENDING 并返回持有租约；已存在时按摘要与状态返回冲突、执行中或已完成记录。
     *
     * @param scope 通道、调用方与 Key 摘要组成的作用域
     * @param requestDigest 请求摘要
     * @param pendingLease 待定租约
     * @return 占位结果
     */
    Mono<Claim> claim(Scope scope, String requestDigest, Duration pendingLease);

    /**
     * 持有者仍为 PENDING 时写入完成记录，保留期从首次接受起算。
     *
     * @param lease 占位租约
     * @param response 需要记录的下游响应
     * @param retention 保留时长
     * @return 是否写入；租约已失效时为 false
     */
    Mono<Boolean> complete(Lease lease, StoredResponse response, Duration retention);

    /**
     * 持有者仍为 PENDING 时删除占位，允许同 Key 再次下发。
     *
     * @param lease 占位租约
     * @return 完成信号
     */
    Mono<Void> release(Lease lease);

    /**
     * @param channel 入口通道
     * @param callerKey 调用方身份；Client API 为 Client ID
     * @param keyHash Idempotency-Key 的 SHA-256 十六进制
     */
    record Scope(String channel, String callerKey, String keyHash) {
        public Scope {
            Objects.requireNonNull(channel, "请求幂等通道不可为空");
            Objects.requireNonNull(callerKey, "请求幂等调用方不可为空");
            Objects.requireNonNull(keyHash, "请求幂等 Key 摘要不可为空");
        }
    }

    /**
     * @param recordKey 存储内的记录标识
     * @param owner 本次占位持有者
     */
    record Lease(String recordKey, String owner) {}

    /**
     * @param status 下游响应状态码
     * @param headers 允许记录的响应头
     * @param body 下游响应体原始字节
     */
    record StoredResponse(int status, Map<String, List<String>> headers, byte[] body) {
        public StoredResponse {
            headers = Map.copyOf(headers);
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        @Override
        public String toString() {
            return "StoredResponse[status=" + status + ", headers=" + headers.keySet() + ", body=[REDACTED]]";
        }
    }

    /**
     * @param outcome 占位结果
     * @param lease 成功占位时的租约
     * @param response 已完成时的记录
     */
    record Claim(ClaimOutcome outcome, Lease lease, StoredResponse response) {}

    enum ClaimOutcome {
        CLAIMED,

        IN_PROGRESS,

        KEY_REUSED,

        COMPLETED
    }
}
