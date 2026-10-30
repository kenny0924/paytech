package com.paytech.payout.domainservice.validator;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.core.configuration.BeneficiaryRequirementProperties;
import com.paytech.payout.core.configuration.BeneficiaryRequirementProperties.FieldRequirement;
import com.paytech.payout.core.configuration.BeneficiaryRequirementProperties.RouteRequirement;
import com.paytech.payout.domain.BeneficiaryAccountDetails;

import lombok.AllArgsConstructor;

/**
 * 按静态字段清单匹配收款方式 × 国家 × 币种，并规范化、校验字段原值（B8、R5）
 * <li>无副作用：不访问数据库或外部服务；提交与字段要求查询两个入口复用。</li>
 * <li>校验失败消息只含字段代码，不含原值。</li>
 */
@Component
@AllArgsConstructor
public class BeneficiaryFieldValidator {
    /** SWIFT 任一国家的清单通配符。 */
    static final String ANY_COUNTRY = "*";

    /** 规范化时去空白、转大写的标识类字段。 */
    static final Set<String> IDENTIFIER_FIELDS = Set.of("iban", "accountNumber", "bankCode", "swiftBic", "accountType");

    private final BeneficiaryRequirementProperties properties;

    /**
     * 查找字段要求：精确国家优先，其次 * 通配
     *
     * @param railType 收款方式
     * @param bankCountry 开户国家
     * @param currency 币种
     * @return 字段要求；清单没有该组合为 null
     */
    public RouteRequirement findRoute(BeneficiaryRailType railType, String bankCountry, String currency) {
        RouteRequirement exact = match(railType, bankCountry, currency);
        return exact != null ? exact : match(railType, ANY_COUNTRY, currency);
    }

    /**
     * 规范化并校验字段：清单外字段、必填缺失、二选一不满足、长度或格式不符均为参数错误
     *
     * @param route 已匹配的字段要求
     * @param raw 请求中的原值
     * @return 规范化后的原值
     */
    public BeneficiaryAccountDetails normalize(RouteRequirement route, BeneficiaryAccountDetails raw) {
        Map<String, String> values = normalizedValues(raw);
        Set<String> allowed = route.getFields().stream().map(FieldRequirement::getCode).collect(Collectors.toSet());
        for (String code : values.keySet()) {
            if (!allowed.contains(code)) {
                throw new InvalidParameterException("字段不适用于该收款方式: " + code);
            }
        }
        route.getFields().forEach(field -> checkField(field, values.get(field.getCode())));
        checkGroups(route.getFields(), values);
        return new BeneficiaryAccountDetails(values.get("holderName"), values.get("iban"), values.get("accountNumber"),
            values.get("bankCode"), values.get("swiftBic"), values.get("bankName"), values.get("accountType"));
    }

    /** 按收款方式、国家与币种精确匹配一个条目。 */
    RouteRequirement match(BeneficiaryRailType railType, String bankCountry, String currency) {
        return properties.getRoutes().stream()
            .filter(route -> route.getRailType().equals(railType.getCode()) && route.getBankCountry().equals(bankCountry)
                && route.getCurrencies().contains(currency))
            .findFirst().orElse(null);
    }

    /** 规范化全部非空字段：标识类去空白转大写，文字类去首尾空白并合并空白；空串视为未填。 */
    static Map<String, String> normalizedValues(BeneficiaryAccountDetails raw) {
        Map<String, String> values = new HashMap<>();
        raw.byCode().forEach((code, value) -> {
            String normalized = IDENTIFIER_FIELDS.contains(code) ? value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT)
                : value.strip().replaceAll("\\s+", " ");
            if (!normalized.isEmpty()) {
                values.put(code, normalized);
            }
        });
        return values;
    }

    /** 单个字段的必填、长度与格式。 */
    static void checkField(FieldRequirement field, String value) {
        if (value == null) {
            if (field.isRequired()) {
                throw new InvalidParameterException("缺少必填字段: " + field.getCode());
            }
            return;
        }
        if (value.length() > field.getMaxLength()) {
            throw new InvalidParameterException("字段超过最大长度: " + field.getCode() + ", 上限 " + field.getMaxLength());
        }
        if (field.getPattern() != null && !value.matches(field.getPattern())) {
            throw new InvalidParameterException("字段格式不符合要求: " + field.getCode());
        }
    }

    /** 二选一分组：每组恰好填一个。 */
    static void checkGroups(List<FieldRequirement> fields, Map<String, String> values) {
        Map<String, List<String>> groups = fields.stream().filter(field -> field.getOneOfGroup() != null)
            .collect(Collectors.groupingBy(FieldRequirement::getOneOfGroup,
                Collectors.mapping(FieldRequirement::getCode, Collectors.toList())));
        groups.forEach((group, codes) -> {
            long filled = codes.stream().filter(values::containsKey).count();
            if (filled != 1) {
                throw new InvalidParameterException("以下字段必须且只能填写一个: " + String.join("、", codes));
            }
        });
    }
}
