package com.paytech.platform.support.dataquery.domainservice.validator;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryLimitsProperties;
import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyRole;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyValueType;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.QueryColumnRule;
import com.paytech.platform.support.dataquery.domain.QueryConstraints;
import com.paytech.platform.support.dataquery.domain.QueryJointColumn;
import com.paytech.platform.support.dataquery.domain.QueryJointConfig;
import com.paytech.platform.support.dataquery.domain.QueryJointKeyParameter;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;
import com.paytech.platform.support.dataquery.engine.QueryEngine;
import com.paytech.platform.support.dataquery.engine.QueryEngines;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;
import com.paytech.platform.support.dataquery.template.QueryParameterDeclaration;
import com.paytech.platform.support.dataquery.template.QueryTemplateEngine;
import com.paytech.platform.support.dataquery.template.TemplateProblem;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;

/**
 * 模板保存校验（B2）：数据源 ACTIVE、约束不超过硬上限、列声明、参数声明结构，再交给模板引擎做语法、引用与展开校验；
 * 有联合查询配置时再校验键类型、键参数与列标注（联合查询 B3）。
 * <li>纯内存校验，不访问数据库或被查库；保存、校验接口与试运行共用。写入时数据源与键类型状态仍由事务内的共享锁重证。</li>
 */
@Component
public class QueryTemplateDraftValidator {

    private static final int MAX_KEEP = 64;

    private static final int MAX_CURSOR_COLUMNS = 8;

    private static final String JOINT = "joint";

    private final QueryEngines engines;

    private final DataQueryLimitsProperties limits;

    private final QueryTemplateEngine templateEngine;

    /**
     * 构造。
     *
     * @param engines 引擎选择
     * @param limits 硬上限
     */
    public QueryTemplateDraftValidator(QueryEngines engines, DataQueryLimitsProperties limits) {
        this.engines = engines;
        this.limits = limits;
        this.templateEngine = new QueryTemplateEngine(limits.templateLimits());
    }

    /**
     * 校验草稿，一次给出全部问题。
     *
     * @param draft 草稿
     * @param dataSource 草稿引用的数据源；不存在时为 null
     * @param keyTypes 联合查询配置引用的键类型，按编码；草稿没有联合查询配置时可为空
     * @return 问题列表；只有 ERROR 阻止保存
     */
    public TemplateValidationResult validate(QueryTemplateDraft draft, DataSourceDetail dataSource,
        Map<String, QueryKeyTypeDetail> keyTypes) {
        List<TemplateProblem> problems = new ArrayList<>();
        checkDataSource(dataSource, problems);
        checkConstraints(draft.getConstraints(), problems);
        checkColumns(draft.getColumns(), problems);
        List<QueryParameterDeclaration> declarations = readDeclarations(draft, problems);
        if (declarations != null) {
            problems.addAll(validateBody(draft, declarations, dataSource).getProblems());
        }
        if (declarations != null && draft.getJoint() != null) {
            checkJoint(draft.getJoint(), declarations, draft.getColumns(), keyTypes, problems);
        }
        return new TemplateValidationResult(List.copyOf(problems));
    }

    /** 联合查询配置：行主体、键参数与列标注。 */
    static void checkJoint(QueryJointConfig joint, List<QueryParameterDeclaration> declarations,
        List<QueryColumnRule> columns, Map<String, QueryKeyTypeDetail> keyTypes, List<TemplateProblem> problems) {
        requireActiveKeyType(joint.getSubjectKeyType(), JOINT + ".subjectKeyType", keyTypes, problems);
        checkKeyParameter(joint.getKeyParameter(), declarations, keyTypes, problems);
        Set<String> names = new HashSet<>();
        for (int index = 0; index < joint.getColumns().size(); index++) {
            QueryJointColumn column = joint.getColumns().get(index);
            String field = JOINT + ".columns[" + index + "]";
            if (!names.add(column.getName().toLowerCase(Locale.ROOT))) {
                problems.add(error(field + ".name", "联合查询列重复: " + column.getName()));
            }
            QueryKeyTypeDetail keyType = requireActiveKeyType(column.getKeyType(), field + ".keyType", keyTypes,
                problems);
            checkColumnRole(column, keyType, columns, field, problems);
        }
    }

    /** 键参数必须已声明，类型与键类型的值类型匹配（INTEGER 键可绑 INTEGER 或 STRING，STRING 键只绑 STRING，均含 LIST）。 */
    static void checkKeyParameter(QueryJointKeyParameter keyParameter, List<QueryParameterDeclaration> declarations,
        Map<String, QueryKeyTypeDetail> keyTypes, List<TemplateProblem> problems) {
        QueryKeyTypeDetail keyType = requireActiveKeyType(keyParameter.getKeyType(), JOINT + ".keyParameter.keyType",
            keyTypes, problems);
        QueryParameterDeclaration declaration = declarations.stream()
            .filter(item -> item.getName().equals(keyParameter.getName())).findFirst().orElse(null);
        if (declaration == null) {
            problems.add(error(JOINT + ".keyParameter.name", "键参数未声明: " + keyParameter.getName()));
            return;
        }
        QueryParameterType type = declaration.getType() == QueryParameterType.LIST ? declaration.getElementType()
            : declaration.getType();
        boolean matched = type == QueryParameterType.STRING
            || type == QueryParameterType.INTEGER && keyType != null && keyType.getValueType() == QueryKeyValueType.INTEGER;
        if (keyType != null && !matched) {
            problems.add(error(JOINT + ".keyParameter.keyType", "参数 " + keyParameter.getName() + " 的类型不能绑定 "
                + keyType.getValueType().getCode() + " 键类型 " + keyType.getKeyTypeCode()
                + "：INTEGER 键可绑 INTEGER 或 STRING 参数，STRING 键只绑 STRING 参数（均含 LIST）"));
        }
    }

    /** 链路列不能屏蔽；默认为维度的键类型标为链路时提醒。 */
    static void checkColumnRole(QueryJointColumn column, QueryKeyTypeDetail keyType, List<QueryColumnRule> columns,
        String field, List<TemplateProblem> problems) {
        if (column.getKeyRole() != QueryKeyRole.LINK) {
            return;
        }
        String name = column.getName().toLowerCase(Locale.ROOT);
        boolean masked = columns.stream().anyMatch(rule -> rule.getName().toLowerCase(Locale.ROOT).equals(name)
            && rule.getMask() != ColumnMaskType.NONE);
        if (masked) {
            problems.add(error(field + ".keyRole", "链路列不能配置屏蔽: " + column.getName()));
        }
        if (keyType != null && keyType.getDefaultRole() == QueryKeyRole.DIMENSION) {
            problems.add(TemplateProblem.field(TemplateProblemLevel.WARNING, field + ".keyRole", "列 " + column.getName()
                + " 会按维度键 " + keyType.getKeyTypeCode() + " 继续展开，可能查出大量数据"));
        }
    }

    /**
     * 引用的键类型必须存在且 ACTIVE。
     *
     * @return 键类型；不存在时为 null
     */
    static QueryKeyTypeDetail requireActiveKeyType(String code, String field, Map<String, QueryKeyTypeDetail> keyTypes,
        List<TemplateProblem> problems) {
        QueryKeyTypeDetail keyType = keyTypes.get(code);
        if (keyType == null || !keyType.active()) {
            problems.add(error(field, "键类型不存在或未启用: " + code));
        }
        return keyType;
    }

    /**
     * 读取参数声明；结构不合法时记为 parameters 字段问题。
     *
     * @param draft 草稿
     * @param problems 问题收集
     * @return 声明；结构不合法时为 null
     */
    List<QueryParameterDeclaration> readDeclarations(QueryTemplateDraft draft, List<TemplateProblem> problems) {
        try {
            return new ParameterDeclarationReader().read(draft.getParameters());
        } catch (InvalidParameterException failure) {
            problems.add(TemplateProblem.field(TemplateProblemLevel.ERROR, "parameters", failure.getMessage()));
            return null;
        }
    }

    /** 正文、引用与展开校验；数据源缺失时只做与数据源无关的部分。 */
    TemplateValidationResult validateBody(QueryTemplateDraft draft, List<QueryParameterDeclaration> declarations,
        DataSourceDetail dataSource) {
        if (dataSource == null) {
            QueryEngine engine = engines.forType(DataSourceEngineType.MYSQL);
            return templateEngine.validate(draft.getBody(), declarations, engine.valueBinder(), rendered -> List.of());
        }
        QueryEngine engine = engines.forType(dataSource.getConnection().getEngineType());
        return templateEngine.validate(draft.getBody(), declarations, engine.valueBinder(),
            engine.renderedQueryCheck(dataSource.getConnection().querySource()));
    }

    /** 数据源必须存在且为 ACTIVE。 */
    static void checkDataSource(DataSourceDetail dataSource, List<TemplateProblem> problems) {
        if (dataSource == null) {
            problems.add(error("dataSourceId", "数据源不存在"));
        } else if (!dataSource.statusIs(DataSourceStatus.ACTIVE)) {
            problems.add(error("dataSourceId", "数据源未启用: " + dataSource.getDataSourceCode()));
        }
    }

    /** 约束不超过硬上限，分页方式与页大小、游标列、总数开关搭配一致。 */
    void checkConstraints(QueryConstraints constraints, List<TemplateProblem> problems) {
        if (constraints.getMaxRows() < 1 || constraints.getMaxRows() > limits.getMaxRows()) {
            problems.add(error("constraints.maxRows", "最大行数必须在 1 到 " + limits.getMaxRows() + " 之间"));
        }
        if (constraints.getTimeoutSeconds() < 1 || constraints.getTimeoutSeconds() > limits.maxTimeoutSeconds()) {
            problems.add(error("constraints.timeoutSeconds", "超时必须在 1 到 " + limits.maxTimeoutSeconds() + " 秒之间"));
        }
        QueryPagingMode mode = constraints.getPagingMode();
        if (mode != QueryPagingMode.NONE) {
            Integer pageSize = constraints.getMaxPageSize();
            if (pageSize == null || pageSize < 1 || pageSize > constraints.getMaxRows()) {
                problems.add(error("constraints.maxPageSize", "偏移或游标分页必须设置页大小上限，且不超过最大行数"));
            }
        }
        if (constraints.isCountAllowed() && mode != QueryPagingMode.OFFSET) {
            problems.add(error("constraints.countAllowed", "只有偏移分页可以允许返回总数"));
        }
        checkCursorColumns(constraints, problems);
    }

    /** 游标分页必须声明 1..8 个不重复的游标列；其他方式不能声明。 */
    static void checkCursorColumns(QueryConstraints constraints, List<TemplateProblem> problems) {
        List<String> columns = constraints.getCursorColumns();
        if (constraints.getPagingMode() != QueryPagingMode.CURSOR) {
            if (!columns.isEmpty()) {
                problems.add(error("constraints.cursorColumns", "只有游标分页可以声明游标列"));
            }
            return;
        }
        Set<String> unique = new HashSet<>();
        columns.forEach(column -> unique.add(column.toLowerCase(Locale.ROOT)));
        if (columns.isEmpty() || columns.size() > MAX_CURSOR_COLUMNS || unique.size() != columns.size()) {
            problems.add(error("constraints.cursorColumns", "游标分页必须声明 1 到 8 个不重复的游标列"));
        }
    }

    /** 列名不重复；只有 KEEP_EDGES 使用保留位数，且不超过 64。 */
    static void checkColumns(List<QueryColumnRule> columns, List<TemplateProblem> problems) {
        Set<String> names = new HashSet<>();
        for (int index = 0; index < columns.size(); index++) {
            QueryColumnRule column = columns.get(index);
            String field = "columns[" + index + "]";
            if (!names.add(column.getName().toLowerCase(Locale.ROOT))) {
                problems.add(error(field + ".name", "列名重复: " + column.getName()));
            }
            boolean keepEdges = column.getMask() == ColumnMaskType.KEEP_EDGES;
            if (!keepEdges && (column.getKeepStart() != 0 || column.getKeepEnd() != 0)) {
                problems.add(error(field + ".mask", "只有 KEEP_EDGES 可以设置保留位数"));
            }
            if (column.getKeepStart() > MAX_KEEP || column.getKeepEnd() > MAX_KEEP) {
                problems.add(error(field + ".keepStart", "保留位数不能超过 " + MAX_KEEP));
            }
        }
    }

    /** 字段级错误。 */
    static TemplateProblem error(String field, String message) {
        return TemplateProblem.field(TemplateProblemLevel.ERROR, field, message);
    }
}
