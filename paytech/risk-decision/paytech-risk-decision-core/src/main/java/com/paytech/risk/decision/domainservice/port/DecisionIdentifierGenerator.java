package com.paytech.risk.decision.domainservice.port;

/**
 * 为 Risk 决策聚合生成本服务拥有的稳定标识。
 */
@FunctionalInterface
public interface DecisionIdentifierGenerator {

    /**
     * 生成下一个决策记录标识。
     *
     * @return 全局唯一的正整数标识
     */
    long nextIdentifier();
}
