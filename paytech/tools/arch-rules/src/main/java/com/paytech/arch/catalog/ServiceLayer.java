package com.paytech.arch.catalog;

/**
 * 逻辑服务所在的业务层次，用于规则 R3 判定领域层是否反向依赖流程层。
 *
 * @author Claude
 */
public enum ServiceLayer {

    /**
     * 领域层：持有领域事实，不得依赖流程层服务的 api。
     */
    DOMAIN,

    /**
     * 流程层：编排领域服务完成交易、出款、争议等流程。
     */
    PROCESS,

    /**
     * 支撑与入口：平台支撑、审批、网关、BFF、Vault，不参与 R3 判定。
     */
    SUPPORT
}
