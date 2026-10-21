package com.paytech.ledger.integration.platform.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.domain.CurrencyRule;
import com.paytech.ledger.integration.platform.PlatformCurrencyClient;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

import lombok.AllArgsConstructor;

/**
 * 只适配 platform-support-api 币种规则查询 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>整份响应先全部校验再返回：任一条不合法即整体失败，避免把半份规则装入快照。</li>
 */
@Component
@AllArgsConstructor
public class PlatformCurrencyClientImpl implements PlatformCurrencyClient {
    /** ISO 4217 三位大写字母。 */
    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

    /** 币种小数位上限。 */
    private static final int MAX_MINOR_UNIT = 8;

    private final CurrencySpecQueryApi currencySpecQueryApi;

    /** 列表必须存在，逐条转换并拒绝重复代码。 */
    @Override
    public Map<String, CurrencyRule> listCurrencyRules() {
        CurrencySpecListResponseBody body = currencySpecQueryApi.listCurrencySpecs();
        if (body == null || body.getItems() == null) {
            throw new ErrorException("Platform 币种规则响应无效: 缺少 items");
        }
        Map<String, CurrencyRule> rules = new HashMap<>();
        for (CurrencySpecResponseBody item : body.getItems()) {
            CurrencyRule rule = toRule(item);
            if (rules.putIfAbsent(rule.getCurrency(), rule) != null) {
                throw new ErrorException("Platform 币种规则响应无效: 币种重复 " + rule.getCurrency());
            }
        }
        return Map.copyOf(rules);
    }

    /** 校验并转换单条规则：代码格式、小数位 0..8、舍入方式与状态可识别。 */
    CurrencyRule toRule(CurrencySpecResponseBody item) {
        if (item == null || item.getCurrency() == null || !CURRENCY_CODE.matcher(item.getCurrency()).matches()) {
            throw new ErrorException("Platform 币种规则响应无效: 币种代码非法 " + (item == null ? null : item.getCurrency()));
        }
        String currency = item.getCurrency();
        if (item.getMinorUnit() < 0 || item.getMinorUnit() > MAX_MINOR_UNIT) {
            throw new ErrorException("Platform 币种规则响应无效: " + currency + " 小数位越界 " + item.getMinorUnit());
        }
        return new CurrencyRule(currency, item.getMinorUnit(),
            parse(CurrencyRoundingMode.class, item.getRoundingMode(), currency),
            parse(CurrencySpecStatus.class, item.getStatus(), currency));
    }

    /** 按稳定 Code 解析枚举；未知或缺失 Code 使整份响应失败关闭。 */
    static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String code, String currency) {
        return Arrays.stream(type.getEnumConstants()).filter(value -> value.getCode().equals(code)).findFirst()
            .orElseThrow(() -> new ErrorException("Platform 币种规则响应无效: " + currency + " 的 "
                + type.getSimpleName() + " 无法识别 " + code));
    }
}
