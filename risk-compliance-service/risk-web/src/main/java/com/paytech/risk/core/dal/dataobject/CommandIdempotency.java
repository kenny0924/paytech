package com.paytech.risk.core.dal.dataobject;

import java.time.Instant;

import com.paytech.risk.domain.enums.CommandIdempotencyStatus;
import com.paytech.risk.domain.enums.CommandOperationType;

/**
 * 命令幂等记录的持久化投影。
 */
public class CommandIdempotency {

    /** 幂等记录标识。 */
    private Long idempotencyId;

    /** Operations 调用方隔离范围。 */
    private String callerScope;

    /** 命令操作类型。 */
    private CommandOperationType operationType;

    /** 调用方提交的幂等键。 */
    private String idempotencyKey;

    /** 规范请求哈希。 */
    private String requestHash;

    /** 可为空的稳定响应资源引用。 */
    private String responseRef;

    /** 当前幂等生命周期状态。 */
    private CommandIdempotencyStatus status;

    /** 首次接收起二十四小时的保留下限。 */
    private Instant expireTime;

    /** 创建 UTC 微秒时间。 */
    private Instant createTime;

    /** 更新 UTC 微秒时间。 */
    private Instant updateTime;

    public Long getIdempotencyId() {
        return idempotencyId;
    }

    public void setIdempotencyId(Long idempotencyId) {
        this.idempotencyId = idempotencyId;
    }

    public String getCallerScope() {
        return callerScope;
    }

    public void setCallerScope(String callerScope) {
        this.callerScope = callerScope;
    }

    public CommandOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(CommandOperationType operationType) {
        this.operationType = operationType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public String getResponseRef() {
        return responseRef;
    }

    public void setResponseRef(String responseRef) {
        this.responseRef = responseRef;
    }

    public CommandIdempotencyStatus getStatus() {
        return status;
    }

    public void setStatus(CommandIdempotencyStatus status) {
        this.status = status;
    }

    public Instant getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Instant expireTime) {
        this.expireTime = expireTime;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }
}
