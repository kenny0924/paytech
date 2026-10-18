package com.paytech.foundation.money.json;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 流式校验原始字段，拒绝重复键而不是允许树模型覆盖。 */
public final class MoneyDeserializer extends ValueDeserializer<Money> {
    @Override
    public Money deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.START_OBJECT)) {
            return context.reportInputMismatch(Money.class, "Money 必须为对象");
        }
        String currency = null;
        String amount = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.PROPERTY_NAME)) {
                return context.reportInputMismatch(Money.class, "Money 字段格式错误");
            }
            String field = parser.currentName();
            if (field.equals("currency") && currency == null) {
                currency = readString(parser, context, 3);
            } else if (field.equals("amount") && amount == null) {
                amount = readString(parser, context, Money.MAX_AMOUNT_TEXT_LENGTH);
            } else {
                return context.reportInputMismatch(Money.class, "Money 不允许未知或重复字段");
            }
        }
        return create(currency, amount, context);
    }

    @Override
    public Money getNullValue(DeserializationContext context) {
        return context.reportInputMismatch(Money.class, "Money 不允许显式 null");
    }

    private String readString(JsonParser parser, DeserializationContext context, int maxLength) {
        if (parser.nextToken() != JsonToken.VALUE_STRING || parser.getStringLength() > maxLength) {
            return context.reportInputMismatch(Money.class, "Money 字段必须为长度受限的字符串");
        }
        return parser.getString();
    }

    private Money create(String currency, String amount, DeserializationContext context) {
        try {
            return Money.of(currency, amount);
        } catch (InvalidParameterException exception) {
            return context.reportInputMismatch(Money.class, "Money 币种或金额不合法");
        }
    }
}
