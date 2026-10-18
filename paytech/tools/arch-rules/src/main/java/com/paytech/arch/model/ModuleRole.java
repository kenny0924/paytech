package com.paytech.arch.model;

/**
 * Maven 模块在 V2 骨架中的角色（design/01 §2）。
 *
 * @author Claude
 */
public enum ModuleRole {

    /**
     * 服务 {@code -api}：HTTP 合同、DTO、错误码与事件 Payload。
     */
    API,

    /**
     * 服务 core：业务实现。包括 {@code -core} 以及按能力拆分的 core 模块（如 {@code -transaction-core}）。
     */
    CORE,

    /**
     * 服务 {@code -app}：唯一可执行模块。
     */
    APP,

    /**
     * 服务 {@code -test}：全部 auto / manual 测试。
     */
    TEST,

    /**
     * 服务 Java Client（如 {@code paytech-vault-client}），对调用方等价于 api。
     */
    CLIENT,

    /**
     * 服务聚合 POM（{@code paytech-<service>}）。
     */
    SERVICE_AGGREGATOR,

    /**
     * Foundation 公共模块。
     */
    FOUNDATION,

    /**
     * 构建治理模块：Reactor 聚合 POM、BOM、父 POM。
     */
    BUILD,

    /**
     * {@code paytech/tools} 下的工具模块。
     */
    TOOL,

    /**
     * 不属于 V2 骨架的模块：第三方制品、搬迁前的旧模块。
     */
    UNCLASSIFIED
}
