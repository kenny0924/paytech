package com.paytech.risk.ext.rest.decision;

import com.paytech.risk.api.decision.request.ListFeatureParameters;
import com.paytech.risk.api.decision.request.VelocityFeatureParameters;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import com.paytech.risk.decision.domain.feature.FeatureSpec;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.RiskVariableModels.AllowedValues;
import com.paytech.risk.api.decision.RiskVariableModels.FormatProfile;
import com.paytech.risk.api.decision.RiskVariableModels.Limits;
import com.paytech.risk.api.decision.RiskVariableModels.Member;
import com.paytech.risk.api.decision.RiskVariableModels.OrderedMember;
import com.paytech.risk.api.decision.RiskVariableModels.SourceCapability;
import com.paytech.risk.api.decision.RiskVariableModels.SourceContract;
import com.paytech.risk.api.decision.RiskVariableModels.SourceField;
import com.paytech.risk.api.decision.RiskVariableModels.SupportedPoint;
import com.paytech.risk.api.decision.RiskVariableModels.VariableReference;
import com.paytech.risk.api.decision.RiskVariableModels.VariableSummary;
import com.paytech.risk.api.decision.request.CreateVariableRequestBody;
import com.paytech.risk.api.decision.request.ReplaceMembersRequestBody;
import com.paytech.risk.api.decision.request.UpdateVariableRequestBody;
import com.paytech.risk.api.decision.response.DeleteVariableResponseBody;
import com.paytech.risk.api.decision.response.ReplaceMembersResponseBody;
import com.paytech.risk.api.decision.response.VariableDetailResponseBody;
import com.paytech.risk.api.decision.response.VariableListResponseBody;
import com.paytech.risk.api.decision.response.VariableManagementContextResponseBody;
import com.paytech.risk.api.decision.response.VariableMembersResponseBody;
import com.paytech.risk.api.decision.response.VariableReferencesResponseBody;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import com.paytech.risk.decision.domain.enums.VariableSource;
import com.paytech.risk.decision.domain.enums.VariableStatus;
import com.paytech.risk.decision.domain.enums.VariableUsage;
import com.paytech.risk.decision.domain.variable.DecisionSourceContracts;
import com.paytech.risk.decision.domain.variable.RiskVariable;
import com.paytech.risk.decision.domain.variable.RiskVariable.Create;
import com.paytech.risk.decision.domain.variable.RiskVariable.Definition;
import com.paytech.risk.decision.domain.variable.RiskVariable.Entry;
import com.paytech.risk.decision.domain.variable.RiskVariable.MemberPage;
import com.paytech.risk.decision.domain.variable.RiskVariable.Update;
import com.paytech.risk.decision.domain.variable.RiskVariable.View;
import com.paytech.risk.decision.domain.variable.VariableAuthoringCapabilities;
import com.paytech.risk.decision.domain.variable.VariableDetailResponse;
import com.paytech.risk.decision.domain.variable.VariableListView;
import com.paytech.risk.decision.domain.variable.VariableReferences;

/** 统一变量 HTTP 模型与 Owner 领域模型之间的显式转换，不查询数据库或执行业务规则。 */
public final class RiskVariableWireConverter {
    /** UTC 六位微秒输出，禁止平台默认时区参与协议格式。 */
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    /** 仅提供固定边界转换。 */
    private RiskVariableWireConverter() { }

    /** 将工程编排能力转换为管理上下文；变量候选仍由目录接口查询。 */
    public static VariableManagementContextResponseBody authoring(VariableAuthoringCapabilities capabilities) {
        Set<VariableSource> creatable = Set.copyOf(capabilities.getCreatableSources());
        return new VariableManagementContextResponseBody(
            capabilities.getSupportedPoints().stream().map(point -> new SupportedPoint(point, point)).toList(),
            capabilities.getSources().stream().map(source -> new SourceCapability(source.getCode(), creatable.contains(source))).toList(),
            capabilities.getValueTypes().stream().map(DecisionValueType::getCode).toList(),
            capabilities.getFormats().stream().map(format -> new FormatProfile(format.getCode(), formatLabel(format), formatLabel(format))).toList(),
            capabilities.getNumericUnits().stream().map(NumericUnit::getCode).toList(),
            new Limits(capabilities.getMemberLimit(), capabilities.getMemberBodyBytes(), capabilities.getLiteralSetLimit()),
            capabilities.getContracts().stream().map(RiskVariableWireConverter::contract).toList());
    }

    /** 将普通分页或编码批取结果转换为目录响应；批取不返回分页字段。 */
    public static VariableListResponseBody list(VariableListView view) {
        RiskVariable.Page page = view.getPage();
        return new VariableListResponseBody(page.items().stream().map(RiskVariableWireConverter::summary).toList(),
            view.getPageNumber(), view.getPageSize(), page.total(), page.missingCodes(), time(page.valuesAsOf()));
    }

    /** 将当前定义和有限预览转换为目录项，不暴露数据对象。 */
    public static VariableSummary summary(View view) {
        Entry entry = view.entry();
        Definition definition = entry.definition();
        return new VariableSummary(definition.variableCode(), entry.variableName(), definition.sourceType().getCode(),
            definition.valueType().getCode(), definition.pointCode(), usages(definition.usages()), definition.systemManaged(),
            entry.status().getCode(), Long.toString(entry.version()), code(definition.numericUnit()), definition.currencyCode(),
            definition.currencyVariableCode(), definition.formatCode().getCode(), definition.allowedValuesVariableCode(),
            entry.currentValue(), view.memberCount(), members(view.previewMembers()), time(entry.updateTime()));
    }

    /** 将当前定义、预览和读取时刻转换为详情，不暴露数据对象。 */
    public static VariableDetailResponseBody detail(VariableDetailResponse response) {
        View view = response.getValue();
        Entry entry = view.entry();
        Definition definition = entry.definition();
        return new VariableDetailResponseBody(definition.variableCode(), entry.variableName(), definition.sourceType().getCode(),
            definition.valueType().getCode(), definition.pointCode(), usages(definition.usages()), definition.systemManaged(),
            entry.status().getCode(), Long.toString(entry.version()), code(definition.numericUnit()), definition.currencyCode(),
            definition.currencyVariableCode(), definition.formatCode().getCode(), definition.allowedValuesVariableCode(),
            entry.currentValue(), view.memberCount(), members(view.previewMembers()), time(entry.updateTime()),
            entry.description(), definition.sourceContractCode(), definition.sourceContractVersion(), definition.sourceField(),
            Long.toString(entry.createBy()), Long.toString(entry.updateBy()), time(entry.createTime()),
            time(response.getValuesAsOf()), allowed(view.allowedValues()));
    }

    /**
     * 将创建 Body 转换为 Owner 创建命令
     *
     * @param body 已完成严格 JSON 与字段约束的创建请求
     * @return 新变量定义与初值
     *         <li>重复用途直接拒绝，不能通过去重隐藏客户端错误。</li>
     *         <li>系统身份固定为 false，工程登记的变量不经本接口创建。</li>
     */
    public static Create create(CreateVariableRequestBody body) {
        Set<VariableUsage> usages = new HashSet<>();
        body.getUsages().forEach(value -> usages.add(parse(VariableUsage.class, value)));
        if (usages.size() != body.getUsages().size()) {
            throw new InvalidParameterException("变量用途不能重复");
        }
        Definition definition = new Definition(body.getVariableCode(), parse(VariableSource.class, body.getSourceType()),
            parse(DecisionValueType.class, body.getValueType()), body.getPointCode(), body.getSourceContractCode(),
            body.getSourceContractVersion(), body.getSourceField(), usages, false, parse(VariableFormat.class, body.getFormatCode()),
            parse(NumericUnit.class, body.getNumericUnit()), body.getCurrencyCode(), body.getCurrencyVariableCode(),
            body.getAllowedValuesVariableCode());
        return new Create(definition, body.getVariableName(), body.getDescription(), body.getCurrentValue(),
            parse(VariableStatus.class, body.getStatus()), feature(body));
    }

    /**
     * 转换 Feature 参数；两类参数至多出现一个，与来源类型的对应关系由 Owner 校验
     *
     * @param body 创建请求
     * @return 实时累计或名单命中参数，均未提供时为空
     */
    static FeatureSpec feature(CreateVariableRequestBody body) {
        if (body.getVelocity() != null && body.getList() != null) {
            throw new InvalidParameterException("实时累计与名单命中参数不能同时提供");
        }
        VelocityFeatureParameters velocity = body.getVelocity();
        if (velocity != null) {
            return new FeatureSpec.Velocity(parseCode(VelocityEventCode.values(), velocity.getEventCode()),
                parseCode(RiskSubjectType.values(), velocity.getAnchorSubjectType()),
                parseCode(VelocityMetric.values(), velocity.getMetric()),
                velocity.getDistinctSubjectType() == null ? null
                    : parseCode(RiskSubjectType.values(), velocity.getDistinctSubjectType()),
                velocity.getWindowSeconds(), velocity.getIncludeCurrent());
        }
        ListFeatureParameters list = body.getList();
        return list == null ? null
            : new FeatureSpec.ListMembership(list.getListCode(), parseCode(RiskSubjectType.values(), list.getSubjectType()));
    }

    /** 按稳定 Code 解析闭合枚举，未知值为参数错误。 */
    static <E extends Enum<E> & com.paytech.foundation.common.enums.CodeEnum> E parseCode(E[] values, String code) {
        return java.util.Arrays.stream(values).filter(value -> value.getCode().equals(code)).findFirst()
            .orElseThrow(() -> new InvalidParameterException("Feature 参数取值无效: " + code));
    }

    /** 将更新 Body 及已解析版本转换为只含可变字段的 Owner 命令。 */
    public static Update update(UpdateVariableRequestBody body, long expectedVersion) {
        return new Update(expectedVersion, body.getVariableName(), body.getDescription(), body.getCurrentValue(),
            parse(VariableStatus.class, body.getStatus()));
    }

    /** 将成员批次按原顺序转换；序号由 Owner 规范化时重新生成。 */
    public static List<RiskVariable.Member> members(ReplaceMembersRequestBody body) {
        return body.getMembers().stream().map(member -> new RiskVariable.Member(member.getValue(), member.getDisplayName(), 0)).toList();
    }

    /** 删除结果只返回墓碑编码、状态和新根版本。 */
    public static DeleteVariableResponseBody deleted(Entry entry) {
        return new DeleteVariableResponseBody(entry.definition().variableCode(), entry.status().getCode(), Long.toString(entry.version()));
    }

    /** 成员分页保留首次根版本及本次实际页码，供后续页校验。 */
    public static VariableMembersResponseBody memberPage(MemberPage page, int pageNumber, int pageSize) {
        return new VariableMembersResponseBody(page.variableCode(), Long.toString(page.version()), page.items().stream()
            .map(member -> new OrderedMember(member.value(), member.displayName(), member.sortOrder())).toList(),
            pageNumber, pageSize, page.total(), time(page.valuesAsOf()));
    }

    /** 整批替换结果返回新根版本和本次提交的成员数量。 */
    public static ReplaceMembersResponseBody replaced(Entry entry, int memberCount) {
        return new ReplaceMembersResponseBody(entry.definition().variableCode(), Long.toString(entry.version()), memberCount,
            time(entry.updateTime()));
    }

    /** 引用页只返回准确位置与续扫游标，不返回任意 URL。 */
    public static VariableReferencesResponseBody references(VariableReferences.Page page) {
        return new VariableReferencesResponseBody(page.items().stream().map(RiskVariableWireConverter::reference).toList(),
            page.nextCursor(), page.hasMore(), page.scannedRows());
    }

    /** 单条引用的 BIGINT 身份按规范十进制字符串输出。 */
    static VariableReference reference(VariableReferences.Reference reference) {
        return new VariableReference(reference.consumerType(), Long.toString(reference.rootId()),
            reference.versionId() == null ? null : reference.versionId().toString(), reference.versionNo(), reference.code(),
            reference.name(), reference.scope(), reference.location(), reference.variableCode());
    }

    /** 已接通的来源合同结构，不构成可脱离变量库保存的字段目录。 */
    static SourceContract contract(DecisionSourceContracts.Contract contract) {
        return new SourceContract(contract.contractCode(), contract.contractVersion(), contract.pointCode(),
            contract.sourceType().getCode(), contract.nodeType(), contract.fields().stream().map(field -> new SourceField(
                field.fieldKey(), field.valueType().getCode(), field.required(), field.dataClassification().getCode(),
                usages(field.allowedUsages()), code(field.numericUnit()), field.currencyFieldKey(), field.semanticRole())).toList());
    }

    /** 选项只返回有限预览，不携带整集合运行快照。 */
    static AllowedValues allowed(RiskVariable.AllowedValues value) {
        if (value == null) {
            return null;
        }
        return new AllowedValues(value.variableCode(), value.variableName(), Long.toString(value.version()),
            value.memberCount(), members(value.previewMembers()));
    }

    /** 预览成员只输出值与显示名称。 */
    static List<Member> members(List<RiskVariable.Member> members) {
        return members.stream().map(member -> new Member(member.value(), member.displayName())).toList();
    }

    /** 用途按 Code 升序输出，保持响应稳定。 */
    static List<String> usages(Set<VariableUsage> usages) {
        return usages.stream().map(VariableUsage::getCode).sorted().toList();
    }

    /** 可空数值量纲按 Code 输出。 */
    static String code(NumericUnit unit) {
        return unit == null ? null : unit.getCode();
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
    static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new InvalidParameterException("变量代码值无效：" + type.getSimpleName());
        }
    }

    /** UTC 六位微秒输出。 */
    static String time(Instant value) {
        return TIME.format(value);
    }
}
