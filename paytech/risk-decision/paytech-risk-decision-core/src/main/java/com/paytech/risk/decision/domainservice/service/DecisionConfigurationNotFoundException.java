package com.paytech.risk.decision.domainservice.service;

import java.io.Serial;
import java.util.NoSuchElementException;

/** 仅表示一次首次 singular lookup 已确定目标不存在；锁后和重放缺口不得使用。 */
public final class DecisionConfigurationNotFoundException extends NoSuchElementException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DecisionConfigurationNotFoundException() {
        super("Risk 决策配置不存在");
    }
}
