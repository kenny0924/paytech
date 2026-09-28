package com.paytech.risk.ext.rest.decision;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.response.VariableDetailResponseBody;
import com.paytech.risk.api.decision.response.VariableManagementContextResponseBody;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.decision.RiskVariableModels.*;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.variable.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.*;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/** 显式 HTTP 与领域投影转换，不承担数据库查询或保存规则。 */
public final class RiskVariableWireConverter {
    /** 仅提供固定边界转换。 */
    private RiskVariableWireConverter() { }

    /** 将当前定义和有限预览转换为目录项，不暴露数据对象。 */
    public static VariableSummary summary(View view) {
        Entry e = view.entry(); Definition d = e.definition();
        return new VariableSummary(
            d.variableCode(),
            e.variableName(),
            d.sourceType().getCode(),
            d.valueType().getCode(),
            d.pointCode(),
            d.usages().stream().map(VariableUsage::getCode).sorted().toList(),
            d.systemManaged(),
            e.status().getCode(),
            Long.toString(e.version()),
            d.numericUnit() == null ? null : d.numericUnit().getCode(),
            d.currencyCode(),
            d.currencyVariableCode(),
            d.formatCode().getCode(),
            d.allowedValuesVariableCode(),
            e.currentValue(),
            view.memberCount(),
            view.previewMembers().stream().map(m -> new com.paytech.risk.api.decision.RiskVariableModels.Member(m.value(), m.displayName())).toList(),
            time(e.updateTime()));
    }

    /** 将当前定义和有限预览转换为详情，不暴露数据对象。 */
    public static VariableDetailResponseBody detail(View view, Instant valuesAsOf) {
        Entry e = view.entry(); Definition d = e.definition();
        return new VariableDetailResponseBody(
            d.variableCode(),
            e.variableName(),
            d.sourceType().getCode(),
            d.valueType().getCode(),
            d.pointCode(),
            d.usages().stream().map(VariableUsage::getCode).sorted().toList(),
            d.systemManaged(),
            e.status().getCode(),
            Long.toString(e.version()),
            d.numericUnit() == null ? null : d.numericUnit().getCode(),
            d.currencyCode(),
            d.currencyVariableCode(),
            d.formatCode().getCode(),
            d.allowedValuesVariableCode(),
            e.currentValue(),
            view.memberCount(),
            view.previewMembers().stream().map(m -> new com.paytech.risk.api.decision.RiskVariableModels.Member(m.value(), m.displayName())).toList(),
            time(e.updateTime()),
            e.description(),
            d.sourceContractCode(),
            d.sourceContractVersion(),
            d.sourceField(),
            Long.toString(e.createBy()),
            Long.toString(e.updateBy()),
            time(e.createTime()),
            time(valuesAsOf),
            allowed(view.allowedValues()));
    }

    /** 选项只返回有限预览，不携带整集合运行快照。 */
    static com.paytech.risk.api.decision.RiskVariableModels.AllowedValues allowed(RiskVariable.AllowedValues value) {
        if (value == null) { return null; }
        return new com.paytech.risk.api.decision.RiskVariableModels.AllowedValues(value.variableCode(), value.variableName(),
            Long.toString(value.version()), value.memberCount(), value.previewMembers().stream().map(m -> new com.paytech.risk.api.decision.RiskVariableModels.Member(m.value(), m.displayName())).toList());
    }

    /** 将严格 JSON 的创建字段转换为固定结构，去重不能隐藏重复用途。 */
    public static Create create(CreateVariableRequestBody body) {
        Set<VariableUsage> usages = new HashSet<>();
        body.usages().forEach(value -> usages.add(parse(VariableUsage.class, value)));
        if (usages.size() != body.usages().size()) { throw new InvalidParameterException("变量用途不能重复"); }
        Definition definition = new Definition(body.variableCode(), parse(VariableSource.class, body.sourceType()),
            parse(DecisionValueType.class, body.valueType()), body.pointCode(), body.sourceContractCode(), body.sourceContractVersion(),
            body.sourceField(), usages, false, parse(VariableFormat.class, body.formatCode()), parse(NumericUnit.class, body.numericUnit()),
            body.currencyCode(), body.currencyVariableCode(), body.allowedValuesVariableCode());
        return new Create(definition, body.variableName(), body.description(), body.currentValue(), parse(VariableStatus.class, body.status()));
    }

    /** 显式组装受支持输入/输出能力；实际变量选项仍必须查询数据库目录。 */
    public static VariableManagementContextResponseBody context(String pointCode) {
        if (pointCode != null && !DecisionSourceContracts.supports(pointCode)) { throw new InvalidParameterException("决策点尚未支持"); }
        var contracts = DecisionSourceContracts.all().stream().filter(c -> pointCode == null || c.pointCode().equals(pointCode));
        return new VariableManagementContextResponseBody(
            DecisionSourceContracts.all().stream().map(c -> c.pointCode()).distinct().map(p -> new SupportedPoint(p, p)).toList(),
            Arrays.stream(VariableSource.values()).map(v -> new SourceCapability(v.getCode(), v != VariableSource.NODE_OUTPUT)).toList(),
            Arrays.stream(DecisionValueType.values()).map(DecisionValueType::getCode).toList(),
            Arrays.stream(VariableFormat.values()).map(v -> new FormatProfile(v.getCode(), formatLabel(v), formatLabel(v))).toList(),
            Arrays.stream(NumericUnit.values()).map(NumericUnit::getCode).toList(), new Limits(1000, 65536, 100),
            contracts.map(c -> new SourceContract(c.contractCode(), c.contractVersion(), c.pointCode(), c.sourceType().getCode(),
                c.nodeType(), c.fields().stream().map(f -> new SourceField(f.fieldKey(), f.valueType().getCode(), f.required(),
                    f.dataClassification().getCode(), f.allowedUsages().stream().map(VariableUsage::getCode).sorted().toList(),
                    f.numericUnit() == null ? null : f.numericUnit().getCode(), f.currencyFieldKey(), f.semanticRole())).toList())).toList());
    }

    /** 通用格式的工程说明；具体变量标签不在前端或后端写死。 */
    static String formatLabel(VariableFormat format) {
        return switch (format) {
            case TEXT -> "文本，保留大小写和前导零";
            case TOKEN -> "大写业务编码";
            case UPPER_ALPHA_2 -> "两位大写字母";
            case UPPER_ALPHA_3 -> "三位大写字母";
            case DIGITS_4 -> "四位数字字符串";
            case BCP47 -> "语言地区标签";
            case POSITIVE_ID -> "正整数标识字符串";
            case NONE -> "无字符串格式";
        };
    }

    /** 精确解析工程枚举，空值是否允许由对应业务合同校验。 */
    public static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) { return null; }
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException failure) { throw new InvalidParameterException("变量代码值无效：" + type.getSimpleName()); }
    }

    /** UTC 六位微秒输出，禁止平台默认时区参与协议格式。 */
    public static String time(Instant value) {
        return new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(value);
    }
}
