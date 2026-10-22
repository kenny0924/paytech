package com.paytech.platform.support.dataquery.domainservice.validator;

import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryLimitsProperties;
import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;
import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.TemplateProblemLevel;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domain.QueryColumnRule;
import com.paytech.platform.support.dataquery.domain.QueryConstraints;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDraft;
import com.paytech.platform.support.dataquery.engine.QueryEngine;
import com.paytech.platform.support.dataquery.engine.QueryEngines;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;
import com.paytech.platform.support.dataquery.template.QueryParameterDeclaration;
import com.paytech.platform.support.dataquery.template.QueryTemplateEngine;
import com.paytech.platform.support.dataquery.template.TemplateProblem;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;

/**
 * 模板保存校验（B2）：数据源 ACTIVE、约束不超过硬上限、列声明、参数声明结构，再交给模板引擎做语法、引用与展开校验。
 * <li>纯内存校验，不访问数据库或被查库；保存、校验接口与试运行共用。写入时数据源状态仍由事务内的共享锁重证。</li>
 */
@Component
public class QueryTemplateDraftValidator {

    private static final int MAX_KEEP = 64;

    private static final int MAX_CURSOR_COLUMNS = 8;

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
     * @return 问题列表；只有 ERROR 阻止保存
     */
    public TemplateValidationResult validate(QueryTemplateDraft draft, DataSourceDetail dataSource) {
        List<TemplateProblem> problems = new ArrayList<>();
        checkDataSource(dataSource, problems);
        checkConstraints(draft.getConstraints(), problems);
        checkColumns(draft.getColumns(), problems);
        List<QueryParameterDeclaration> declarations = readDeclarations(draft, problems);
        if (declarations != null) {
            problems.addAll(validateBody(draft, declarations, dataSource).getProblems());
        }
        return new TemplateValidationResult(List.copyOf(problems));
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
