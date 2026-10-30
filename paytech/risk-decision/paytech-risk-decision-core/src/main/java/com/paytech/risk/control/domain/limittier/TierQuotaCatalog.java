package com.paytech.risk.control.domain.limittier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.paytech.risk.control.core.enums.LimitTier;

/**
 * 按命名约定从风控常量解析的四档 × 币种额度（O-1：额度值就是风控常量，引擎不改）
 * <li>常量 TIER_{TIER}_{CCY}_{SINGLE|DAY|MONTH}：MONEY 单位，币种取名称中的 {CCY}；常量的币种来源可以是固定币种（须等于 {CCY}），
 * 也可以是支付点的输入币种变量（规则以“输入币种 = {CCY}”限定，见档位配置说明）；集合 TIER_{TIER}_CURRENCIES：本档位允许的币种。</li>
 * <li>本档位的币种：有集合时取集合成员，集合外的常量记为配置问题；没有集合时取常量的币种（非 STANDARD 档位同时记为配置问题）。</li>
 * <li>纯解析，不读库；不同币种互不换算。</li>
 */
public final class TierQuotaCatalog {
    private static final Pattern CONSTANT = Pattern.compile(
        "TIER_(VERIFICATION|OBSERVATION|STANDARD|RESTRICTED)_([A-Z]{3})_(SINGLE|DAY|MONTH)");

    private static final Pattern COLLECTION = Pattern.compile("TIER_(VERIFICATION|OBSERVATION|STANDARD|RESTRICTED)_CURRENCIES");

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    /**
     * 一个币种的三个额度，未配置为空
     *
     * @param currency ISO 4217 币种
     * @param single 单笔上限
     * @param day UTC 当日上限
     * @param month 近 30 个 UTC 自然日（含当日）上限
     */
    public record CurrencyQuota(String currency, BigDecimal single, BigDecimal day, BigDecimal month) { }

    private final Map<LimitTier, List<CurrencyQuota>> quotas;

    private final List<String> issues;

    /** 由解析结果构造。 */
    private TierQuotaCatalog(Map<LimitTier, List<CurrencyQuota>> quotas, List<String> issues) {
        this.quotas = quotas;
        this.issues = issues;
    }

    /**
     * 解析 TIER_ 前缀变量
     *
     * @param variables 编码以 TIER_ 开头的 ACTIVE 常量与集合
     * @return 四档额度与配置问题
     */
    public static TierQuotaCatalog parse(List<TierQuotaVariable> variables) {
        Map<LimitTier, Map<String, BigDecimal[]>> values = new EnumMap<>(LimitTier.class);
        Map<LimitTier, Set<String>> sets = new EnumMap<>(LimitTier.class);
        List<String> issues = new ArrayList<>();
        for (TierQuotaVariable variable : variables) {
            if (variable.collection()) {
                readCollection(variable, sets, issues);
            } else {
                readConstant(variable, values, issues);
            }
        }
        Map<LimitTier, List<CurrencyQuota>> quotas = new EnumMap<>(LimitTier.class);
        for (LimitTier tier : LimitTier.values()) {
            quotas.put(tier, tierQuotas(tier, values.getOrDefault(tier, Map.of()), sets.get(tier), issues));
        }
        return new TierQuotaCatalog(quotas, List.copyOf(issues));
    }

    /** 本档位已配置币种的额度，按币种代码排序。 */
    public List<CurrencyQuota> quotas(LimitTier tier) {
        return quotas.get(tier);
    }

    /** 命名不规范、缺项或集合与常量不一致等问题。 */
    public List<String> issues() {
        return issues;
    }

    /** 在任一档位配置过、但本档位未配置的币种，按代码排序。 */
    public List<String> unconfiguredCurrencies(LimitTier tier) {
        Set<String> all = new TreeSet<>();
        quotas.values().forEach(list -> list.forEach(quota -> all.add(quota.currency())));
        quotas.get(tier).forEach(quota -> all.remove(quota.currency()));
        return List.copyOf(all);
    }

    /** 读取一个币种集合变量。 */
    static void readCollection(TierQuotaVariable variable, Map<LimitTier, Set<String>> sets, List<String> issues) {
        Matcher matcher = COLLECTION.matcher(variable.code());
        if (!matcher.matches()) {
            issues.add("无法识别的档位额度集合变量: " + variable.code());
            return;
        }
        Set<String> members = new TreeSet<>();
        for (String member : variable.members()) {
            if (member != null && CURRENCY.matcher(member).matches()) {
                members.add(member);
            } else {
                issues.add(variable.code() + " 含非币种代码的成员");
            }
        }
        sets.put(LimitTier.valueOf(matcher.group(1)), members);
    }

    /** 读取一个额度常量，币种或单位不符时记问题并忽略。 */
    static void readConstant(TierQuotaVariable variable, Map<LimitTier, Map<String, BigDecimal[]>> values,
        List<String> issues) {
        Matcher matcher = CONSTANT.matcher(variable.code());
        if (!matcher.matches()) {
            issues.add("无法识别的档位额度常量: " + variable.code());
            return;
        }
        String currency = matcher.group(2);
        boolean currencyMatches = variable.currencyCode() == null || currency.equals(variable.currencyCode());
        if (!variable.money() || !currencyMatches || variable.numberValue() == null
            || variable.numberValue().signum() < 0) {
            issues.add(variable.code() + " 须为币种 " + currency + "（或输入币种）的非负金额常量");
            return;
        }
        BigDecimal[] slots = values.computeIfAbsent(LimitTier.valueOf(matcher.group(1)), key -> new TreeMap<>())
            .computeIfAbsent(currency, key -> new BigDecimal[3]);
        slots[slot(matcher.group(3))] = variable.numberValue();
    }

    /** 额度名称对应的位置：SINGLE、DAY、MONTH。 */
    static int slot(String period) {
        return switch (period) {
            case "SINGLE" -> 0;
            case "DAY" -> 1;
            default -> 2;
        };
    }

    /** 组装一个档位的币种额度并记录缺项与不一致。 */
    static List<CurrencyQuota> tierQuotas(LimitTier tier, Map<String, BigDecimal[]> values, Set<String> set,
        List<String> issues) {
        String setCode = "TIER_" + tier.getCode() + "_CURRENCIES";
        if (set == null && !values.isEmpty() && !tier.allowsUnconfiguredCurrency()) {
            issues.add("缺少币种集合 " + setCode + "，本档位的额度常量不会放行任何币种");
        }
        Set<String> currencies = new TreeSet<>(set == null ? values.keySet() : set);
        if (set != null) {
            values.keySet().stream().filter(currency -> !set.contains(currency))
                .forEach(currency -> issues.add("TIER_" + tier.getCode() + "_" + currency + "_* 的币种不在 " + setCode));
        }
        List<CurrencyQuota> result = new ArrayList<>();
        for (String currency : currencies) {
            BigDecimal[] slots = values.getOrDefault(currency, new BigDecimal[3]);
            reportMissing(tier, currency, slots, issues);
            result.add(new CurrencyQuota(currency, slots[0], slots[1], slots[2]));
        }
        return List.copyOf(result);
    }

    /** 记录一个币种缺少的额度常量。 */
    static void reportMissing(LimitTier tier, String currency, BigDecimal[] slots, List<String> issues) {
        String[] names = {"SINGLE", "DAY", "MONTH"};
        for (int index = 0; index < names.length; index++) {
            if (slots[index] == null) {
                issues.add("未配置 TIER_" + tier.getCode() + "_" + currency + "_" + names[index]);
            }
        }
    }
}
