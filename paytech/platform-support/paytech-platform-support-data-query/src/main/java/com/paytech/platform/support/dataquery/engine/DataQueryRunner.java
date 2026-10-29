package com.paytech.platform.support.dataquery.engine;

import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.dataquery.core.configuration.DataQueryLimitsProperties;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.domain.DataQueryResult;
import com.paytech.platform.support.dataquery.domain.DataQueryRun;
import com.paytech.platform.support.dataquery.domain.QueryColumnRule;
import com.paytech.platform.support.dataquery.domain.QueryConstraints;
import com.paytech.platform.support.dataquery.domain.QueryCursorCodec;
import com.paytech.platform.support.dataquery.integration.DataSourcePoolRegistry;
import com.paytech.platform.support.dataquery.template.ParameterBindingResult;
import com.paytech.platform.support.dataquery.template.ParameterDeclarationReader;
import com.paytech.platform.support.dataquery.template.QueryTemplate;
import com.paytech.platform.support.dataquery.template.QueryTemplateEngine;
import com.paytech.platform.support.dataquery.template.RenderedQuery;
import com.paytech.platform.support.dataquery.template.SystemParameters;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * 执行与试运行共用的第 3—8 步（technique 4.2）：参数、渲染、只读校验与分页改写、借出连接池执行、屏蔽与游标、观测。
 * <li>访问被查库，只能在数据库事务外调用；不写 mor_platform。</li>
 * <li>观测只记一条元数据日志（模板、版本、数据源、调用方、操作人、结果、行数、耗时），不含参数值与结果行；
 * 另记指标 data.query.execution{template,result}。</li>
 */
@Component
public class DataQueryRunner {

    private static final Logger log = LoggerFactory.getLogger(DataQueryRunner.class);

    private static final String SUCCEEDED = "SUCCEEDED";

    private final MeterRegistry meters;

    private final QueryEngines engines;

    private final DataSourcePoolRegistry pools;

    private final DataQueryLimitsProperties limits;

    private final QueryTemplateEngine templateEngine;

    /**
     * 构造。
     *
     * @param meters 指标
     * @param engines 引擎选择
     * @param pools 连接池注册表
     * @param limits 硬上限
     */
    public DataQueryRunner(MeterRegistry meters, QueryEngines engines, DataSourcePoolRegistry pools,
        DataQueryLimitsProperties limits) {
        this.meters = meters;
        this.engines = engines;
        this.pools = pools;
        this.limits = limits;
        this.templateEngine = new QueryTemplateEngine(limits.templateLimits());
    }

    /**
     * 执行一次查询。
     *
     * @param run 输入
     * @return 结果
     * @throws RequestBodyValidationException 参数字段错误（422）
     */
    public DataQueryResult run(DataQueryRun run) {
        long startedAt = System.nanoTime();
        String outcome = SUCCEEDED;
        int rowCount = 0;
        try {
            DataQueryResult result = execute(run);
            rowCount = result.getRows().size();
            return result;
        } catch (RuntimeException failure) {
            outcome = outcome(failure);
            throw failure;
        } finally {
            log.info("数据查询执行 template={} version={} dataSource={} caller={} operator={} result={} rows={} elapsedMs={}",
                run.getTemplateLabel(), run.getVersionNo(), run.getDataSource().getDataSourceCode(),
                run.getCallerService(), run.getOperatorAccountId() == null ? "-" : run.getOperatorAccountId(), outcome,
                rowCount,
                (System.nanoTime() - startedAt) / 1_000_000L);
            meters.counter("data.query.execution", "template", run.getTemplateLabel(), "result", outcome).increment();
        }
    }

    /** 第 3—7 步。 */
    DataQueryResult execute(DataQueryRun run) {
        QueryEngine engine = engines.forType(run.getDataSource().getConnection().getEngineType());
        QueryConstraints constraints = run.getContent().getConstraints();
        QueryPaging paging = paging(constraints, run);
        Map<String, Object> cursorValues = cursorValues(constraints, run);
        ParameterBindingResult bound = templateEngine.bindParameters(
            new ParameterDeclarationReader().read(run.getContent().getParameters()), run.getParams(),
            new SystemParameters(run.getOperatorAccountId(), cursorValues));
        requireNoParameterErrors(bound);
        QueryTemplate template = templateEngine.parse(run.getContent().getBody());
        RenderedQuery rendered = templateEngine.render(template, bound.getValues(), engine.valueBinder());
        PreparedQuery prepared = engine.prepare(rendered, run.getDataSource().getConnection().querySource(), paging);
        QueryBudget budget = new QueryBudget(timeoutSeconds(constraints, run), limits.getMaxResponseBytes().toBytes());
        QueryResult raw;
        try (DataSourceHandle handle = pools.lease(run.getDataSource())) {
            raw = engine.execute(handle, prepared, budget);
        }
        return shape(run, constraints, prepared, raw);
    }

    /**
     * 本次超时秒数：min(模板 timeoutSeconds, 系统上限, 调用方上限)；调用方上限为 null 时不参与。
     *
     * @param constraints 模板约束
     * @param run 输入
     * @return 超时秒数
     */
    int timeoutSeconds(QueryConstraints constraints, DataQueryRun run) {
        int seconds = Math.min(constraints.getTimeoutSeconds(), limits.maxTimeoutSeconds());
        return run.getTimeoutCapSeconds() == null ? seconds : Math.min(seconds, run.getTimeoutCapSeconds());
    }

    /**
     * 按模板约束与系统上限确定分页。
     *
     * @param constraints 约束
     * @param run 输入
     * @return 分页
     */
    QueryPaging paging(QueryConstraints constraints, DataQueryRun run) {
        QueryPagingMode mode = constraints.getPagingMode();
        if (run.isWithTotal() && !(mode == QueryPagingMode.OFFSET && constraints.isCountAllowed())) {
            throw new BizCheckFailException("该模板不允许返回总数");
        }
        if (run.getCursor() != null && mode != QueryPagingMode.CURSOR) {
            throw new InvalidParameterException("该模板不是游标分页，不能传 cursor");
        }
        if (mode == QueryPagingMode.NONE) {
            return QueryPaging.none(Math.min(constraints.getMaxRows(), limits.getMaxRows()));
        }
        int size = pageSize(constraints, run.getSize());
        if (mode == QueryPagingMode.CURSOR) {
            return QueryPaging.cursor(size);
        }
        int page = run.getPage() == null ? 1 : run.getPage();
        long offset = Math.multiplyExact((long) page - 1, size);
        if (offset > limits.getMaxOffset()) {
            throw new InvalidParameterException("偏移量不能超过 " + limits.getMaxOffset() + " 行");
        }
        return QueryPaging.offset(size, offset, run.isWithTotal());
    }

    /** 页大小：缺省为模板上限，且不超过模板上限与系统行数上限。 */
    int pageSize(QueryConstraints constraints, Integer requested) {
        int maximum = Math.min(constraints.getMaxPageSize(), limits.getMaxRows());
        int size = requested == null ? maximum : requested;
        if (size < 1 || size > maximum) {
            throw new InvalidParameterException("size 必须在 1 到 " + maximum + " 之间");
        }
        return size;
    }

    /** 游标分页解码 cursor 注入 __cursor；首页为 null。 */
    static Map<String, Object> cursorValues(QueryConstraints constraints, DataQueryRun run) {
        if (constraints.getPagingMode() != QueryPagingMode.CURSOR || run.getCursor() == null) {
            return null;
        }
        return QueryCursorCodec.decode(run.getCursor(), run.getVersionNo());
    }

    /** 参数字段错误按公共 422 VALIDATION_ERROR 返回（A 卡裁决 2）。 */
    static void requireNoParameterErrors(ParameterBindingResult bound) {
        if (!bound.getErrors().isEmpty()) {
            throw new RequestBodyValidationException(bound.getErrors().stream()
                .map(error -> new ProblemFieldError(error.getField(), error.getMessage())).toList(), null);
        }
    }

    /** 第 7 步：游标取屏蔽前的值，再按列声明屏蔽。 */
    static DataQueryResult shape(DataQueryRun run, QueryConstraints constraints, PreparedQuery prepared,
        QueryResult raw) {
        String nextCursor = constraints.getPagingMode() == QueryPagingMode.CURSOR
            ? QueryResultShaper.nextCursor(raw, constraints.getCursorColumns(), run.getVersionNo()) : null;
        List<QueryColumnRule> rules = QueryResultShaper.rulesFor(raw.getColumns(), run.getContent().getColumns());
        List<DataQueryResult.Column> columns = new ArrayList<>();
        for (int index = 0; index < raw.getColumns().size(); index++) {
            QueryColumnRule rule = rules.get(index);
            if (rule != null && rule.getMask() == ColumnMaskType.HIDE) {
                continue;
            }
            String name = raw.getColumns().get(index);
            columns.add(new DataQueryResult.Column(name, rule == null || rule.getLabel() == null ? name
                : rule.getLabel()));
        }
        List<List<Object>> rows = raw.getRows().stream().map(row -> QueryResultShaper.maskRow(row, rules)).toList();
        List<String> bindings = prepared.getBindings().stream().map(value -> value == null ? null
            : String.valueOf(value)).toList();
        return new DataQueryResult(run.getVersionNo(), List.copyOf(columns), rows, raw.isHasMore(), raw.getTotal(),
            nextCursor, raw.getElapsedMillis(), prepared.getSql(), bindings, prepared.getCountSql());
    }

    /** 指标与日志的结果标签：有错误码时用错误码，否则用异常类型。 */
    static String outcome(RuntimeException failure) {
        if (failure instanceof FailException fail && fail.getErrorCode() != null) {
            return fail.getErrorCode().getCode();
        }
        if (failure instanceof ErrorException error && error.getErrorCode() != null) {
            return error.getErrorCode().getCode();
        }
        return failure.getClass().getSimpleName();
    }
}
