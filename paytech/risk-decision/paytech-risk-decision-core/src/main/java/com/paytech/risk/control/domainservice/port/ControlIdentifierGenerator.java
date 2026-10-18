package com.paytech.risk.control.domainservice.port;

/** 为控制模块记录生成本服务拥有的稳定标识；与其他模块共享同一 Snowflake 实例。 */
@FunctionalInterface
public interface ControlIdentifierGenerator {

    /**
     * 生成下一个控制记录标识。
     *
     * @return 全局唯一的正整数标识
     */
    long nextIdentifier();
}
