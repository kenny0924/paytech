package com.paytech.foundation.money.json;

import com.paytech.foundation.money.Money;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** 金额按币种精度输出主单位十进制字符串，避免客户端浮点精度损失。 */
public final class MoneySerializer extends ValueSerializer<Money> {
    /** 补齐币种小数位，不换算最小单位、不舍入金额。 */
    @Override
    public void serialize(Money value, JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject();
        generator.writeStringProperty("currency", value.getCurrency());
        generator.writeStringProperty("amount", value.toAmountString());
        generator.writeEndObject();
    }
}
