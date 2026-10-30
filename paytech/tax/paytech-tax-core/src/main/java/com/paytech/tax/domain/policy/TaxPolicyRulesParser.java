package com.paytech.tax.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.core.enums.LiableRole;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 地区包 policy_json（Schema 版本 1）的解析、结构校验与互斥校验（technique 9.1，regional-policies 第 3 节）
 * <li>只接受已登记的区块与字段，未知区块、字段或取值一律拒绝；条件语法限定为已登记字段的 EQ（字符串）/ IN（字符串数组）合取。</li>
 * <li>treatments 两两互斥：任意两条规则可能同时命中即拒绝，不靠"最后一条赢"。</li>
 * <li>纯函数，不访问数据库；失败抛出 {@link TaxPolicyInvalidException}，消息只含字段路径。</li>
 */
public final class TaxPolicyRulesParser {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private static final Pattern RATE = Pattern.compile("0(\\.[0-9]{1,10})?");

    private static final Pattern SERIES_PREFIX = Pattern.compile("[A-Z]{2}-[A-Z0-9]{1,16}");

    private static final Pattern NUMBER_FORMAT =
        Pattern.compile("[A-Za-z0-9{}\\-/]*\\{series}[A-Za-z0-9\\-/]*\\{number:0[1-9]}[A-Za-z0-9\\-/]*");

    private static final Pattern TEXT_KEY = Pattern.compile("[a-z][A-Za-z0-9_.]{0,127}");

    private static final Set<String> TOP_LEVEL = Set.of("schemaVersion", "applicability", "placeOfSupply", "taxPoint",
        "treatments", "unmatchedBehavior", "calculation", "documents", "reporting");

    private static final Set<String> ROUNDING = Set.of("HALF_UP", "HALF_EVEN", "DOWN", "UP");

    /** 纯静态解析。 */
    private TaxPolicyRulesParser() {
    }

    /**
     * 解析并校验地区包内容
     *
     * @param policyJson t_tax_policy.policy_json
     * @return 不可变规则
     * @throws TaxPolicyInvalidException JSON 不合法、结构不符或规则不互斥
     */
    public static TaxPolicyRules parse(String policyJson) {
        JsonNode root = readObject(policyJson);
        requireOnly(root, TOP_LEVEL, "");
        if (!root.path("schemaVersion").isIntegralNumber() || root.path("schemaVersion").asInt() != 1) {
            throw new TaxPolicyInvalidException("schemaVersion");
        }
        requireText(root, "unmatchedBehavior", Set.of("BLOCK"), "");
        JsonNode applicability = optionalObject(root, "applicability", Set.of("supplyKinds", "notCollectedReasonCode", "remarks"));
        JsonNode taxPoint = object(root, "taxPoint", Set.of("basis", "quoteValidityHours"));
        JsonNode calculation = object(root, "calculation", Set.of("method", "roundingMethod", "roundingScale", "roundingLevel"));
        JsonNode reporting = object(root, "reporting", Set.of("reportingRuleCode", "dueDateRule", "dueDays"));
        requireText(object(root, "placeOfSupply", Set.of("algorithm")), "algorithm", Set.of("BUYER_COUNTRY"), "placeOfSupply.");
        requireText(calculation, "method", Set.of("PERCENTAGE"), "calculation.");
        requireText(calculation, "roundingLevel", Set.of("LINE"), "calculation.");
        String dueDateRule = requireText(reporting, "dueDateRule", Set.of("PERIOD_END_PLUS_DAYS", "END_OF_NEXT_MONTH"), "reporting.");
        return new TaxPolicyRules(supplyKinds(applicability), optionalCode(applicability, "notCollectedReasonCode", "applicability."),
            "BUYER_COUNTRY", requireText(taxPoint, "basis", Set.of("PAYMENT"), "taxPoint."),
            optionalInt(taxPoint, "quoteValidityHours", 1, 720, TaxPolicyRules.DEFAULT_QUOTE_VALIDITY_HOURS, "taxPoint."),
            treatments(root.path("treatments")),
            RoundingMode.valueOf(requireText(calculation, "roundingMethod", ROUNDING, "calculation.")),
            calculation.has("roundingScale") ? optionalInt(calculation, "roundingScale", 0, 8, 0, "calculation.") : null,
            receipt(root), requireCode(reporting, "reportingRuleCode", "reporting."), dueDateRule,
            dueDays(reporting, dueDateRule));
    }

    /** 根对象。 */
    static JsonNode readObject(String policyJson) {
        try {
            JsonNode root = policyJson == null ? null : JSON.readTree(policyJson);
            if (root == null || !root.isObject()) {
                throw new TaxPolicyInvalidException("$");
            }
            return root;
        } catch (JacksonException malformed) {
            throw new TaxPolicyInvalidException("$");
        }
    }

    /** 处理规则数组：非空、规则代码唯一、逐条校验并两两互斥。 */
    static List<TaxTreatmentRule> treatments(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            throw new TaxPolicyInvalidException("treatments");
        }
        List<TaxTreatmentRule> rules = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            TaxTreatmentRule rule = treatment(array.get(index), "treatments[" + index + "].");
            if (!codes.add(rule.getRuleCode())) {
                throw new TaxPolicyInvalidException("treatments[" + index + "].ruleCode");
            }
            checkExclusive(rules, rule, index);
            rules.add(rule);
        }
        return List.copyOf(rules);
    }

    /** 新规则不能与已有任何规则重叠。 */
    static void checkExclusive(List<TaxTreatmentRule> existing, TaxTreatmentRule rule, int index) {
        for (TaxTreatmentRule other : existing) {
            if (other.overlaps(rule)) {
                throw new TaxPolicyInvalidException("treatments[" + index + "].when overlaps " + other.getRuleCode());
            }
        }
    }

    /** 一条处理规则：ruleCode、when、result。 */
    static TaxTreatmentRule treatment(JsonNode node, String path) {
        if (!node.isObject()) {
            throw new TaxPolicyInvalidException(path.substring(0, path.length() - 1));
        }
        requireOnly(node, Set.of("ruleCode", "when", "result"), path);
        JsonNode result = object(node, "result", Set.of("componentCode", "treatment", "liableRole", "reasonCode", "rate"), path);
        TaxTreatment treatment = enumCode(TaxTreatment.class, requireCode(result, "treatment", path + "result."),
            path + "result.treatment");
        BigDecimal rate = rate(result, treatment, path + "result.");
        return new TaxTreatmentRule(requireCode(node, "ruleCode", path), conditions(node.path("when"), path + "when"),
            requireCode(result, "componentCode", path + "result."), treatment,
            enumCode(LiableRole.class, requireCode(result, "liableRole", path + "result."), path + "result.liableRole"),
            requireCode(result, "reasonCode", path + "result."), rate);
    }

    /** 条件：只允许登记字段；值为字符串（EQ）或非空字符串数组（IN）。 */
    static Map<String, Set<String>> conditions(JsonNode when, String path) {
        if (!when.isObject()) {
            throw new TaxPolicyInvalidException(path);
        }
        requireOnly(when, Set.copyOf(TaxPolicyRules.CONDITION_FIELDS), path + ".");
        Map<String, Set<String>> conditions = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : when.properties()) {
            conditions.put(entry.getKey(), values(entry.getValue(), path + "." + entry.getKey()));
        }
        return Map.copyOf(conditions);
    }

    /** 条件取值集合。 */
    static Set<String> values(JsonNode value, String path) {
        if (value.isString() && CODE.matcher(value.asString()).matches()) {
            return Set.of(value.asString());
        }
        if (!value.isArray() || value.isEmpty()) {
            throw new TaxPolicyInvalidException(path);
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isString() || !CODE.matcher(item.asString()).matches()) {
                throw new TaxPolicyInvalidException(path);
            }
            values.add(item.asString());
        }
        return Set.copyOf(values);
    }

    /**
     * 税率：TAXABLE 必须为 (0, 1) 的小数字符串；ZERO_RATED 为空或 0；其他处理必须为空（不虚构 0%）。
     */
    static BigDecimal rate(JsonNode result, TaxTreatment treatment, String path) {
        JsonNode node = result.path("rate");
        if (node.isMissingNode() || node.isNull()) {
            if (treatment == TaxTreatment.TAXABLE) {
                throw new TaxPolicyInvalidException(path + "rate");
            }
            return null;
        }
        if (!node.isString() || !RATE.matcher(node.asString()).matches()) {
            throw new TaxPolicyInvalidException(path + "rate");
        }
        BigDecimal rate = new BigDecimal(node.asString());
        boolean valid = treatment == TaxTreatment.TAXABLE ? rate.signum() > 0
            : treatment == TaxTreatment.ZERO_RATED && rate.signum() == 0;
        if (!valid) {
            throw new TaxPolicyInvalidException(path + "rate");
        }
        return rate;
    }

    /** documents.receipt；documents 缺失时不开票。 */
    static TaxReceiptRule receipt(JsonNode root) {
        JsonNode documents = optionalObject(root, "documents", Set.of("receipt"));
        if (documents == null || !documents.has("receipt")) {
            return null;
        }
        String path = "documents.receipt.";
        JsonNode receipt = object(documents, "receipt", Set.of("seriesPrefix", "numberFormat", "templateCode",
            "templateRevision", "titleKey", "legalTextKeys", "submission"), "documents.");
        requireText(receipt, "submission", Set.of("NOT_REQUIRED"), path);
        return new TaxReceiptRule(matched(receipt, "seriesPrefix", SERIES_PREFIX, path),
            matched(receipt, "numberFormat", NUMBER_FORMAT, path), requireCode(receipt, "templateCode", path),
            optionalInt(receipt, "templateRevision", 1, 1000, 1, path), matched(receipt, "titleKey", TEXT_KEY, path),
            textKeys(receipt.path("legalTextKeys"), path + "legalTextKeys"));
    }

    /** 法定文字键数组。 */
    static List<String> textKeys(JsonNode array, String path) {
        if (!array.isArray()) {
            throw new TaxPolicyInvalidException(path);
        }
        List<String> keys = new ArrayList<>();
        for (JsonNode item : array) {
            if (!item.isString() || !TEXT_KEY.matcher(item.asString()).matches()) {
                throw new TaxPolicyInvalidException(path);
            }
            keys.add(item.asString());
        }
        return List.copyOf(keys);
    }

    /** applicability.supplyKinds；缺失时为 SALE。 */
    static Set<String> supplyKinds(JsonNode applicability) {
        if (applicability == null || !applicability.has("supplyKinds")) {
            return Set.of("SALE");
        }
        return values(applicability.path("supplyKinds"), "applicability.supplyKinds");
    }

    /** PERIOD_END_PLUS_DAYS 必须带 1–366 的 dueDays；END_OF_NEXT_MONTH 不允许 dueDays。 */
    static Integer dueDays(JsonNode reporting, String dueDateRule) {
        if ("END_OF_NEXT_MONTH".equals(dueDateRule)) {
            if (reporting.has("dueDays")) {
                throw new TaxPolicyInvalidException("reporting.dueDays");
            }
            return null;
        }
        if (!reporting.has("dueDays")) {
            throw new TaxPolicyInvalidException("reporting.dueDays");
        }
        return optionalInt(reporting, "dueDays", 1, 366, 0, "reporting.");
    }

    /** 必填对象区块，且只含允许字段。 */
    static JsonNode object(JsonNode parent, String field, Set<String> allowed) {
        return object(parent, field, allowed, "");
    }

    /** 必填对象区块（带路径前缀），且只含允许字段。 */
    static JsonNode object(JsonNode parent, String field, Set<String> allowed, String path) {
        JsonNode node = parent.path(field);
        if (!node.isObject()) {
            throw new TaxPolicyInvalidException(path + field);
        }
        requireOnly(node, allowed, path + field + ".");
        return node;
    }

    /** 可选对象区块；缺失返回 null。 */
    static JsonNode optionalObject(JsonNode parent, String field, Set<String> allowed) {
        return parent.has(field) ? object(parent, field, allowed) : null;
    }

    /** 对象只能含允许字段。 */
    static void requireOnly(JsonNode node, Set<String> allowed, String path) {
        for (String name : node.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new TaxPolicyInvalidException(path + name);
            }
        }
    }

    /** 必填字符串且在允许值内。 */
    static String requireText(JsonNode node, String field, Set<String> allowed, String path) {
        JsonNode value = node.path(field);
        if (!value.isString() || !allowed.contains(value.asString())) {
            throw new TaxPolicyInvalidException(path + field);
        }
        return value.asString();
    }

    /** 必填大写代码。 */
    static String requireCode(JsonNode node, String field, String path) {
        return matched(node, field, CODE, path);
    }

    /** 可选大写代码；缺失或 null 返回 null。 */
    static String optionalCode(JsonNode node, String field, String path) {
        if (node == null || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        return requireCode(node, field, path);
    }

    /** 必填且符合格式的字符串。 */
    static String matched(JsonNode node, String field, Pattern pattern, String path) {
        JsonNode value = node.path(field);
        if (!value.isString() || !pattern.matcher(value.asString()).matches()) {
            throw new TaxPolicyInvalidException(path + field);
        }
        return value.asString();
    }

    /** 可选整数，缺失取缺省值；给出时必须在 [min, max]。 */
    static int optionalInt(JsonNode node, String field, int min, int max, int defaultValue, String path) {
        JsonNode value = node.path(field);
        if (value.isMissingNode()) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || value.asInt() < min || value.asInt() > max) {
            throw new TaxPolicyInvalidException(path + field);
        }
        return value.asInt();
    }

    /** 按 Code 解析封闭枚举；未知 Code 视为不合法。 */
    static <E extends CodeEnum> E enumCode(Class<E> type, String code, String path) {
        E value = EnumUtils.getEnumByCode(type, code);
        if (value == null) {
            throw new TaxPolicyInvalidException(path);
        }
        return value;
    }
}
