package com.paytech.risk.control.domain.limittier;

import java.math.BigDecimal;
import java.util.List;

/**
 * 编码以 TIER_ 开头的 ACTIVE 风控常量或集合变量，按档位额度命名约定解析前的原值
 *
 * @param code 变量编码
 * @param collection true 为集合变量（成员为币种代码），false 为常量
 * @param numberValue 常量数值；集合为空
 * @param money 常量是否为金额（MONEY 单位）
 * @param currencyCode 金额常量的固定币种；币种来源为输入币种变量或非金额时为空
 * @param members 集合成员值；常量为空集合
 */
public record TierQuotaVariable(String code, boolean collection, BigDecimal numberValue, boolean money,
    String currencyCode, List<String> members) { }
