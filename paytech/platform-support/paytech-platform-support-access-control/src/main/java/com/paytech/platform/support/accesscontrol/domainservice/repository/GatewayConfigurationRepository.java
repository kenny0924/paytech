package com.paytech.platform.support.accesscontrol.domainservice.repository;

import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewayConfigurationProjection.LoadedConfiguration;

/**
 * 读取当前 Gateway 配置的脱离持久化上下文快照。
 */
public interface GatewayConfigurationRepository {

    /**
     * 使用固定次数集合查询读取当前配置。事务边界由调用方提供。
     *
     * @return 不包含可变 DO 的脱离式配置快照
     */
    LoadedConfiguration loadCurrentConfiguration();
}
