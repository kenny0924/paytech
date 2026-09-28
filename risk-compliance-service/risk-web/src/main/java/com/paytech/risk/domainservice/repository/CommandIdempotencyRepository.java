package com.paytech.risk.domainservice.repository;

import java.time.Instant;

import com.paytech.risk.core.dal.dataobject.CommandIdempotency;
import com.paytech.risk.domainservice.service.CommandIdempotencyService.CommandIdentity;
import com.paytech.risk.domainservice.service.CommandIdempotencyService.ResponseReference;

/**
 * 命令幂等记录的受锁访问边界。
 */
public interface CommandIdempotencyRepository {

    /**
     * 尝试登记精确幂等身份；重复身份返回同一写事务中锁住的既有行。
     *
     * @param identity 调用方、操作和键组成的唯一身份
     * @param requestHash 完整规范请求的哈希
     * @param firstNow 首次接收时间
     * @return 首次登记或受锁既有记录
     */
    LockedCommandClaim claim(CommandIdentity identity, String requestHash, Instant firstNow);

    /**
     * 锁住既有幂等行，供同一短写事务内重分类。
     *
     * @param identity 精确幂等身份
     * @return 已锁定的记录；不存在时为 {@code null}
     */
    CommandIdempotency lock(CommandIdentity identity);

    /**
     * 记录 Validate 事务 A 已产生的稳定 Release 引用。
     *
     * @param identity 精确幂等身份
     * @param requestHash 原始请求哈希
     * @param responseReference Release 稳定引用
     * @param now 当前写入时间
     * @return 受条件更新的影响行数
     */
    int recordValidateProcessingReference(
        CommandIdentity identity,
        String requestHash,
        ResponseReference responseReference,
        Instant now
    );

    /**
     * 在当前 {@code PROCESSING} 记录上固定首次成功事实。
     *
     * @param identity 精确幂等身份
     * @param requestHash 原始请求哈希
     * @param responseReference 稳定首次响应引用
     * @param now 当前写入时间
     * @return 受条件更新的影响行数
     */
    int complete(
        CommandIdentity identity,
        String requestHash,
        ResponseReference responseReference,
        Instant now
    );

    /**
     * 一个声明动作的结果。
     *
     * @param first 是否刚插入初始记录
     * @param record 初始记录或同一事务中锁住的既有记录
     */
    record LockedCommandClaim(boolean first, CommandIdempotency record) {
    }
}
