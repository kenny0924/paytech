package com.paytech.billing.integration.platform;

import java.util.List;

import com.paytech.billing.domain.CurrencyRule;

/** 读取 Platform 全部币种规则；必须在数据库事务外调用，只供币种快照刷新使用。 */
public interface PlatformCurrencyClient {

    /**
     * 读取 Platform 全部状态的币种规则
     *
     * @return 全部规则；整份响应校验通过才返回
     * @throws com.paytech.foundation.common.error.ErrorException 响应缺失字段、代码格式非法、小数位越界、舍入方式或状态不可识别、
     *         币种重复
     * @throws RuntimeException Platform 不可用或拒绝；调用方保留旧快照
     */
    List<CurrencyRule> listCurrencyRules();
}
