package com.paytech.billing.integration.platform.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.paytech.billing.core.enums.BillingCodes;
import com.paytech.billing.domain.CurrencyRule;
import com.paytech.billing.integration.platform.PlatformCurrencyClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.CurrencySpecQueryApi;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

import lombok.AllArgsConstructor;

/**
 * 只适配 platform-support-api 币种规则查询 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>整份响应逐条校验后才返回，任一条不合法整体拒绝，避免快照被部分替换。</li>
 */
@Component
@AllArgsConstructor
public class PlatformCurrencyClientImpl implements PlatformCurrencyClient {
    /** ISO 4217 三位大写字母。 */
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    /** 币种精度上限，与 Platform 维护规则一致。 */
    private static final int MAX_MINOR_UNIT = 8;

    private final CurrencySpecQueryApi currencySpecQueryApi;

    /** 读取并校验全部币种规则；币种代码不得重复。 */
    @Override
    public List<CurrencyRule> listCurrencyRules() {
        CurrencySpecListResponseBody body = currencySpecQueryApi.listCurrencySpecs();
        if (body == null || body.getItems() == null) {
            throw new ErrorException("Platform 币种规则响应无效: 缺少 items");
        }
        List<CurrencyRule> rules = new ArrayList<>();
        Set<String> currencies = new HashSet<>();
        for (CurrencySpecResponseBody item : body.getItems()) {
            CurrencyRule rule = toRule(item);
            if (!currencies.add(rule.getCurrency())) {
                throw new ErrorException("Platform 币种规则响应无效: 币种重复 " + rule.getCurrency());
            }
            rules.add(rule);
        }
        return rules;
    }

    /** 单条规则校验与转换：代码格式、小数位范围、舍入方式与状态必须可识别。 */
    CurrencyRule toRule(CurrencySpecResponseBody item) {
        if (item == null || item.getCurrency() == null || !CURRENCY.matcher(item.getCurrency()).matches()) {
            throw new ErrorException("Platform 币种规则响应无效: 币种代码非法 " + (item == null ? null : item.getCurrency()));
        }
        if (item.getMinorUnit() < 0 || item.getMinorUnit() > MAX_MINOR_UNIT) {
            throw new ErrorException("Platform 币种规则响应无效: 小数位越界 " + item.getCurrency() + ", " + item.getMinorUnit());
        }
        try {
            return new CurrencyRule(item.getCurrency(), item.getMinorUnit(),
                BillingCodes.parse(CurrencyRoundingMode.class, "舍入方式", item.getRoundingMode()),
                BillingCodes.parse(CurrencySpecStatus.class, "币种状态", item.getStatus()));
        } catch (InvalidParameterException unknown) {
            throw new ErrorException("Platform 币种规则响应无效: " + item.getCurrency() + ", " + unknown.getMessage(), unknown);
        }
    }
}
