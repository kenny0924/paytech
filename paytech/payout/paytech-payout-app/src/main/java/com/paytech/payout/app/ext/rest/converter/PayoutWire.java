package com.paytech.payout.app.ext.rest.converter;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.MDC;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.money.Money;
import com.paytech.foundation.web.request.RequestIdFilter;

/** payout HTTP Wire 的公共转换：字符串主键、枚举 Code、金额与来源键。 */
public final class PayoutWire {
    private PayoutWire() {
    }

    /** 可空主键字符串转 Long（格式已由合同校验）。 */
    public static Long id(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    /** 可空主键转字符串。 */
    public static String text(Long value) {
        return value == null ? null : value.toString();
    }

    /** 可空枚举 Code。 */
    public static String code(CodeEnum value) {
        return value == null ? null : value.getCode();
    }

    /** 按 Code 解析可空枚举（取值已由合同正则校验）。 */
    public static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String code) {
        if (code == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return constant;
            }
        }
        throw new InvalidParameterException("不支持的取值: " + code);
    }

    /** ownerType 与 ownerId 必须同时给出或同时省略。 */
    public static void requireOwnerPair(String ownerType, String ownerId) {
        if ((ownerType == null) != (ownerId == null)) {
            throw new InvalidParameterException("ownerType 与 ownerId 必须同时给出");
        }
    }

    /** 按币种精度补齐的主单位金额字符串；为空时为空。 */
    public static String amount(String currency, BigDecimal value) {
        return value == null ? null : Money.of(currency, value).toAmountString();
    }

    /** 来源键：当前请求的 Request ID（RequestIdFilter 已校验格式）；没有时生成一次性标识。 */
    public static String sourceKey() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId != null ? requestId : "payout-" + UUID.randomUUID();
    }
}
