package com.paytech.risk.control.domain.limittier;

/**
 * 一个 Client 的迁移结果
 *
 * @param clientId Client ID
 * @param created true 为新设 STANDARD，false 为已有档位跳过
 */
public record TierMigrationResult(long clientId, boolean created) { }
