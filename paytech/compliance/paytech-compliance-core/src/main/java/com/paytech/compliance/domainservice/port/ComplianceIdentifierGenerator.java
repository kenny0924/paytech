package com.paytech.risk.compliance.domainservice.port;

/** 为合规聚合生成本服务拥有的稳定标识；与决策模块共享同一 Snowflake 实例。 */
@FunctionalInterface
public interface ComplianceIdentifierGenerator {

    /**
     * 生成下一个合规记录标识。
     *
     * @return 全局唯一的正整数标识
     */
    long nextIdentifier();
}
