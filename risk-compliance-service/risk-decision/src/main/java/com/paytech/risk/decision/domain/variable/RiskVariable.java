package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.enums.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 变量库发布给同进程调用者的业务模型；不暴露数据库行对象。 */
public final class RiskVariable {
    /** 仅提供模型命名空间。 */
    private RiskVariable() { }

    /** 创建后固定的结构；全局范围仅适用于常量和集合。 */
    public record Definition(String variableCode, VariableSource sourceType, DecisionValueType valueType,
        String pointCode, String sourceContractCode, Integer sourceContractVersion, String sourceField,
        Set<VariableUsage> usages, boolean systemManaged, VariableFormat formatCode, NumericUnit numericUnit,
        String currencyCode, String currencyVariableCode, String allowedValuesVariableCode) {
        /** 防御性复制用途，防止共享元数据在校验后被调用方修改。 */
        public Definition { usages = Set.copyOf(usages); }
    }

    /** 当前常量仅为规范字符串或 Boolean；输入/输出从不伪造实际值。 */
    public record Entry(Definition definition, String variableName, String description, Object currentValue,
        VariableStatus status, long version, long createBy, long updateBy, Instant createTime, Instant updateTime) { }

    /** 单个集合成员；业务值已规范化，成员行标识不构成前端身份。 */
    public record Member(Object value, String displayName, int sortOrder) { }

    /** 可选值关联的当前有限预览；完整列表通过成员接口分页。 */
    public record AllowedValues(String variableCode, String variableName, long version, long memberCount,
        List<Member> previewMembers) { }

    /** 当前详情/目录需要的领域投影，不进入规则或流程内容摘要。 */
    public record View(Entry entry, Long memberCount, List<Member> previewMembers, AllowedValues allowedValues) { }

    /** 新变量定义及初值，账号与时刻由可信入口另行提供。 */
    public record Create(Definition definition, String variableName, String description, Object currentValue,
        VariableStatus status) { }

    /** 只允许修改展示文字、当前常量和可用状态。 */
    public record Update(long expectedVersion, String variableName, String description, Object currentValue,
        VariableStatus status) { }

    /** 普通查询与指定编码批取互斥；pattern 已按 SQL LIKE 字面转义。 */
    public record Query(String pattern, VariableSource sourceType, DecisionValueType valueType,
        VariableStatus status, String pointCode, VariableUsage usage, String nodeType,
        List<String> codes, int page, int size) { }

    /** 分页事实由数据库拥有；批取时 total 为空且返回 missingCodes。 */
    public record Page(List<View> items, Long total, List<String> missingCodes, Instant valuesAsOf) { }

    /** 根版本与成员来自同一快照，翻页携带根版本检测整批更新。 */
    public record MemberPage(String variableCode, long version, List<Member> items, long total, Instant valuesAsOf) { }

    /** 当前值读取同时加载直接依赖；缺失/损坏引用由展示问题标记，不重写历史。 */
    public record Catalog(Map<String, View> variables, List<Issue> issues, Instant valuesAsOf) { }

    /** 可定位的目录问题；不携带输入事实或敏感值。 */
    public record Issue(String variableCode, String code, String message) { }
}
