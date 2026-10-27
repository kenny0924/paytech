package com.paytech.client.master.domain.onboarding;

import static com.paytech.client.contract.api.enums.ClientDraftErrorCode.INVALID_DRAFT;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import com.paytech.client.contract.api.onboarding.ClientPeopleModels.RelationFields;
import com.paytech.client.contract.api.onboarding.ClientPeopleModels.RelationPatchRequestBody;
import com.paytech.client.master.core.dal.dataobject.ClientPartyRelation;
import com.paytech.client.master.core.dal.dataobject.RelationType;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;

/**
 * 草稿关系的取值与语法校验；关系类型是 t_relation_type 目录代码，是否存在与持股、UBO 依据是否适用由 {@link #requireCatalog} 按目录判断
 * <li>不推断任何辖区的 UBO 门槛，门槛只在提交校验按规则开关计算。</li>
 * <li>effectiveFrom 未提供时取当日（UTC）：入驻关系自登记当天生效。</li>
 * <li>任何问题都以 INVALID_DRAFT 加 errors[] 拒绝，字段路径为 relation.&lt;字段&gt;（结构问题为 relation、dirtyFields 或 values）。</li>
 *
 * @param relationType 关系类型代码
 * @param ownershipPercentage 持股比例，可空
 * @param effectiveFrom 生效时间（UTC 日初）
 * @param effectiveTo 结束时间，可空
 * @param uboBasis UBO 认定依据，可空
 * @param jobTitle 职务，可空
 * @param nomineeFlag 是否名义持有，可空
 * @param controlNature 控制方式说明，可空
 */
public record ClientRelationValues(String relationType, BigDecimal ownershipPercentage, Instant effectiveFrom, Instant effectiveTo,
    String uboBasis, String jobTitle, Boolean nomineeFlag, String controlNature) {
    private static final Set<String> FIELDS = Set.of("relationType", "ownershipPercentage", "effectiveFrom", "effectiveTo", "uboBasis",
        "jobTitle", "nomineeFlag", "controlNature");
    private static final Set<String> UBO_BASIS = Set.of("OWNERSHIP", "CONTROL", "SENIOR_MANAGING_OFFICIAL");

    /** 新建人员时一起创建的关系。 */
    public static ClientRelationValues create(RelationFields fields) {
        if (fields == null) {
            throw invalid("relation", "须提供关系");
        }
        return complete(normalize(raw(fields.relationType(), fields.ownershipPercentage(), fields.effectiveFrom(),
            fields.effectiveTo(), fields.uboBasis(), fields.jobTitle(), fields.nomineeFlag(), fields.controlNature())));
    }

    /** 关系补丁：dirtyFields 与 values 键集合一致且只含已知字段。 */
    public static Map<String, String> patch(RelationPatchRequestBody request) {
        if (request == null || request.dirtyFields() == null || request.dirtyFields().isEmpty()
            || request.dirtyFields().size() > FIELDS.size() || new HashSet<>(request.dirtyFields()).size() != request.dirtyFields().size()) {
            throw invalid("dirtyFields", "须列出不重复的修改字段");
        }
        if (request.values() == null || !request.values().keySet().equals(new HashSet<>(request.dirtyFields()))) {
            throw invalid("values", "取值须与 dirtyFields 一一对应");
        }
        return normalize(request.values());
    }

    /** 在已保存关系上合并补丁。 */
    public static ClientRelationValues merge(ClientPartyRelation current, Map<String, String> patch) {
        var values = new TreeMap<String, String>();
        values.put("relationType", current.relationType());
        values.put("ownershipPercentage", decimal(current.ownershipPercentage()));
        values.put("effectiveFrom", date(current.effectiveFrom()));
        values.put("effectiveTo", date(current.effectiveTo()));
        values.put("uboBasis", current.uboBasis());
        values.put("jobTitle", current.jobTitle());
        values.put("nomineeFlag", current.nomineeFlag() == null ? null : current.nomineeFlag().toString());
        values.put("controlNature", current.controlNature());
        values.putAll(patch);
        return complete(values);
    }

    /**
     * 按关系类型目录判断：类型须为 ACTIVE 目录项；只有需填持股的类型可填比例，只有计入 UBO 的类型可填认定依据
     *
     * @param type 目录行；不存在或已下线时为 null
     */
    public void requireCatalog(RelationType type) {
        if (type == null) {
            throw invalid("relation.relationType", "当前辖区不支持此关系类型");
        }
        if (ownershipPercentage != null && !Boolean.TRUE.equals(type.getOwnershipPctRequired())) {
            throw invalid("relation.ownershipPercentage", "该关系类型不填写持股比例");
        }
        if (uboBasis != null && !Boolean.TRUE.equals(type.getUboFlag())) {
            throw invalid("relation.uboBasis", "该关系类型不填写受益人认定依据");
        }
    }

    /** 组装原始取值。 */
    private static Map<String, String> raw(String type, String percentage, String from, String to, String uboBasis, String jobTitle,
        String nominee, String controlNature) {
        var values = new LinkedHashMap<String, String>();
        values.put("relationType", type);
        values.put("ownershipPercentage", percentage);
        values.put("effectiveFrom", from);
        values.put("effectiveTo", to);
        values.put("uboBasis", uboBasis);
        values.put("jobTitle", jobTitle);
        values.put("nomineeFlag", nominee);
        values.put("controlNature", controlNature);
        return values;
    }

    /** 逐字段去空白并校验语法；空串视为清空。 */
    private static Map<String, String> normalize(Map<String, String> input) {
        var errors = new ArrayList<ProblemFieldError>();
        input.keySet().stream().filter(field -> !FIELDS.contains(field)).sorted()
            .forEach(field -> errors.add(new ProblemFieldError("relation." + field, "不支持的关系字段")));
        var result = new TreeMap<String, String>();
        input.forEach((field, raw) -> {
            if (!FIELDS.contains(field)) {
                return;
            }
            String value = raw == null ? null : raw.strip();
            if (value == null || value.isEmpty()) {
                result.put(field, null);
                return;
            }
            String checked = field(field, value);
            if (checked == null) {
                errors.add(new ProblemFieldError("relation." + field, message(field)));
            } else {
                result.put(field, checked);
            }
        });
        if (!errors.isEmpty()) {
            throw new RequestBodyValidationException(INVALID_DRAFT, errors, null);
        }
        return Collections.unmodifiableMap(result);
    }

    /** 单字段的外显约束说明，不回显原值。 */
    private static String message(String field) {
        return switch (field) {
            case "relationType" -> "须为大写字母开头的关系类型代码";
            case "ownershipPercentage" -> "须为大于 0 且不超过 100 的数字，最多 10 位小数";
            case "uboBasis" -> "须为 OWNERSHIP、CONTROL 或 SENIOR_MANAGING_OFFICIAL";
            case "jobTitle" -> "不能超过 128 个字符";
            case "controlNature" -> "不能超过 255 个字符";
            case "nomineeFlag" -> "须为 true 或 false";
            default -> "须为 YYYY-MM-DD 日期";
        };
    }

    /** 单字段语法；不合规返回 null。 */
    private static String field(String field, String value) {
        try {
            return switch (field) {
                case "relationType" -> value.matches("[A-Z][A-Z0-9_]{0,63}") ? value : null;
                case "ownershipPercentage" -> percentage(value);
                case "uboBasis" -> UBO_BASIS.contains(value) ? value : null;
                case "jobTitle" -> value.codePointCount(0, value.length()) <= 128 ? value : null;
                case "controlNature" -> value.codePointCount(0, value.length()) <= 255 ? value : null;
                case "nomineeFlag" -> "true".equals(value) || "false".equals(value) ? value : null;
                default -> value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && LocalDate.parse(value).getYear() >= 1000 ? value : null;
            };
        } catch (IllegalArgumentException | DateTimeException malformed) {
            return null;
        }
    }

    /** 0 到 100、最多 10 位小数；不合规返回 null。 */
    private static String percentage(String value) {
        if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,10})?")) {
            return null;
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("100")) > 0) {
            return null;
        }
        return decimal(amount);
    }

    /** 必填项齐全并构造；未提供生效日期时取当日（UTC）。 */
    private static ClientRelationValues complete(Map<String, String> values) {
        if (values.get("relationType") == null) {
            throw invalid("relation.relationType", "须选择关系类型");
        }
        var percentage = values.get("ownershipPercentage") == null ? null : new BigDecimal(values.get("ownershipPercentage"));
        var from = values.get("effectiveFrom") == null ? LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)
            : instant(values.get("effectiveFrom"));
        var to = instant(values.get("effectiveTo"));
        if (to != null && !to.isAfter(from)) {
            throw invalid("relation.effectiveTo", "结束日期须晚于生效日期");
        }
        Boolean nominee = values.get("nomineeFlag") == null ? null : Boolean.valueOf(values.get("nomineeFlag"));
        return new ClientRelationValues(values.get("relationType"), percentage, from, to, values.get("uboBasis"),
            values.get("jobTitle"), nominee, values.get("controlNature"));
    }

    /** UTC 日初时间点转日期。 */
    public static String date(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** 十进制字符串。 */
    public static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    /** 日期转 UTC 日初时间点。 */
    private static Instant instant(String value) {
        return value == null ? null : LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** 单字段拒绝为 INVALID_DRAFT 加 errors[]。 */
    private static RequestBodyValidationException invalid(String field, String message) {
        return new RequestBodyValidationException(INVALID_DRAFT, List.of(new ProblemFieldError(field, message)), null);
    }

    /** 与已保存关系的全部取值是否相同；比例按数值比较。 */
    public boolean matches(ClientPartyRelation existing) {
        return relationType.equals(existing.relationType())
            && (ownershipPercentage == null ? existing.ownershipPercentage() == null
                : existing.ownershipPercentage() != null && ownershipPercentage.compareTo(existing.ownershipPercentage()) == 0)
            && Objects.equals(effectiveFrom, existing.effectiveFrom()) && Objects.equals(effectiveTo, existing.effectiveTo())
            && Objects.equals(uboBasis, existing.uboBasis()) && Objects.equals(jobTitle, existing.jobTitle())
            && Objects.equals(nomineeFlag, existing.nomineeFlag()) && Objects.equals(controlNature, existing.controlNature());
    }
}
