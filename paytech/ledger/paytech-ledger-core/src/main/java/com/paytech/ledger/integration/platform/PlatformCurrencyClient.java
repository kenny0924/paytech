package com.paytech.ledger.integration.platform;

import java.util.Map;

import com.paytech.ledger.domain.CurrencyRule;

/** 读取 platform-support 的全部币种规则；必须在数据库事务外调用，只由快照刷新使用。 */
public interface PlatformCurrencyClient {

    /**
     * 读取并整体校验全部币种规则
     *
     * @return 币种代码 → 规则的不可变映射；Platform 没有规则时为空
     * @throws com.paytech.foundation.common.error.ErrorException 响应不合法（代码格式、小数位、舍入方式、状态或重复代码）
     * @throws RuntimeException Platform 不可用或拒绝；调用方保留上一份快照
     */
    Map<String, CurrencyRule> listCurrencyRules();
}
