package com.paytech.platform.support.dataquery.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.dataquery.template.RenderedQueryCheck;
import com.paytech.platform.support.dataquery.template.RenderedQueryViolation;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.ASTNodeAccess;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.ParseException;
import net.sf.jsqlparser.parser.Token;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;

/**
 * 渲染后 SQL 的只读校验（B5 第 3 层），保存时对每个展开组合、执行前对实际 SQL 各做一次。
 *
 * <p>依次检查：字符串字面量外的注释符号；恰好一条 SELECT（PlainSelect、SetOperationList，可带 WITH）；
 * 任何一层的 INTO、FOR UPDATE / FOR SHARE / LOCK IN SHARE MODE；黑名单函数；系统库引用；
 * 数据源无默认库时未限定库名的表；顶层 LIMIT / OFFSET / FETCH。解析器无法解析的语句一律拒绝。</p>
 */
public final class ReadOnlySqlGuard {

    private static final Set<String> BLOCKED_FUNCTIONS = Set.of("SLEEP", "BENCHMARK", "GET_LOCK", "RELEASE_LOCK",
        "RELEASE_ALL_LOCKS", "IS_FREE_LOCK", "IS_USED_LOCK", "LOAD_FILE", "MASTER_POS_WAIT", "SOURCE_POS_WAIT",
        "WAIT_FOR_EXECUTED_GTID_SET", "NAME_CONST");

    private static final String DUAL = "dual";

    /**
     * 校验 SQL，返回全部拒绝原因。
     *
     * @param sql 渲染后的 SQL，值以 ? 表示
     * @param source 数据源事实
     * @return 校验结果
     */
    public SqlInspection inspect(String sql, QuerySource source) {
        List<RenderedQueryViolation> violations = new ArrayList<>();
        SqlTextScanner scanner = new SqlTextScanner(sql);
        inspectText(scanner, violations);
        if (!violations.isEmpty()) {
            return new SqlInspection(null, List.copyOf(violations));
        }
        Select select = parseSingleSelect(sql, violations);
        if (select == null) {
            return new SqlInspection(null, List.copyOf(violations));
        }
        inspectTopLevel(select, scanner, violations);
        SelectTreeInspector tree = new SelectTreeInspector();
        tree.getTables((Statement) select);
        inspectSelects(tree, sql, scanner, violations);
        inspectTables(tree, sql, source, violations);
        inspectFunctions(tree, sql, violations);
        return new SqlInspection(violations.isEmpty() ? select : null, List.copyOf(violations));
    }

    /**
     * 要求 SQL 通过只读校验，执行路径使用。
     *
     * @param sql 渲染后的 SQL
     * @param source 数据源事实
     * @return 语法树，供分页改写
     * @throws BizCheckFailException 未通过，消息列出全部原因
     */
    public Select requireReadOnly(String sql, QuerySource source) {
        SqlInspection inspection = inspect(sql, source);
        if (!inspection.passed()) {
            throw new BizCheckFailException("只读校验未通过：" + inspection.getViolations().stream()
                .map(RenderedQueryViolation::getMessage).collect(Collectors.joining("；")));
        }
        return inspection.getSelect();
    }

    /**
     * 供模板保存校验使用的检查：对渲染正文执行本校验。
     *
     * @param source 数据源事实
     * @return 渲染结果检查
     */
    public RenderedQueryCheck renderedQueryCheck(QuerySource source) {
        return rendered -> inspect(rendered.getText(), source).getViolations();
    }

    /**
     * 文本层：注释符号、INTO、LOCK IN SHARE MODE。INTO 与 LOCK 也在语法树上检查，这里先给出明确原因。
     *
     * @param scanner 文本扫描结果
     * @param violations 拒绝原因收集
     */
    private static void inspectText(SqlTextScanner scanner, List<RenderedQueryViolation> violations) {
        if (scanner.commentOffset() >= 0) {
            violations.add(new RenderedQueryViolation(scanner.commentOffset(),
                "不能包含 SQL 注释（/* */、--、#），模板注释请用 {{!-- --}}"));
        }
        int into = scanner.findSequence("INTO");
        if (into >= 0) {
            violations.add(new RenderedQueryViolation(into, "不允许 INTO（含 INTO OUTFILE、INTO DUMPFILE、INTO 变量）"));
        }
        int lock = scanner.findSequence("LOCK", "IN", "SHARE", "MODE");
        if (lock >= 0) {
            violations.add(new RenderedQueryViolation(lock, "不允许 LOCK IN SHARE MODE"));
        }
    }

    /**
     * 解析并要求恰好一条 SELECT。
     *
     * @param sql SQL
     * @param violations 拒绝原因收集
     * @return 语法树；不满足时为 null
     */
    private static Select parseSingleSelect(String sql, List<RenderedQueryViolation> violations) {
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException exception) {
            violations.add(new RenderedQueryViolation(parseErrorOffset(sql, exception), "解析器不支持该语法"));
            return null;
        }
        if (statements == null || statements.size() != 1) {
            violations.add(new RenderedQueryViolation(0, "只允许一条语句"));
            return null;
        }
        Statement statement = statements.getFirst();
        if (!(statement instanceof PlainSelect) && !(statement instanceof SetOperationList)) {
            violations.add(new RenderedQueryViolation(0, statement instanceof Select
                ? "只允许 SELECT、UNION 或 WITH … SELECT" : "只允许 SELECT 语句"));
            return null;
        }
        return (Select) statement;
    }

    /**
     * 顶层不能自带 LIMIT / OFFSET / FETCH，分页由服务追加；UNION 顶层的加锁子句也在这里检查。
     *
     * @param select 语法树
     * @param scanner 文本扫描结果，用于定位
     * @param violations 拒绝原因收集
     */
    private static void inspectTopLevel(Select select, SqlTextScanner scanner, List<RenderedQueryViolation> violations) {
        if (select.getLimit() != null || select.getOffset() != null || select.getFetch() != null) {
            violations.add(new RenderedQueryViolation(Math.max(0, scanner.findLast("LIMIT", "OFFSET", "FETCH")),
                "正文不能带顶层 LIMIT / OFFSET，分页由服务追加"));
        }
        if (!(select instanceof PlainSelect) && select.getForMode() != null) {
            violations.add(new RenderedQueryViolation(Math.max(0, scanner.findSequence("FOR")),
                "不允许 FOR UPDATE / FOR SHARE"));
        }
    }

    /**
     * 每一层查询都不能带 INTO 或加锁子句。
     *
     * @param tree 语法树收集结果
     * @param sql SQL
     * @param scanner 文本扫描结果
     * @param violations 拒绝原因收集
     */
    private static void inspectSelects(SelectTreeInspector tree, String sql, SqlTextScanner scanner,
        List<RenderedQueryViolation> violations) {
        for (PlainSelect plainSelect : tree.plainSelects()) {
            boolean into = plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()
                || plainSelect.getMySqlSelectIntoClause() != null || plainSelect.getIntoTempTable() != null;
            if (into) {
                violations.add(new RenderedQueryViolation(offsetOf(plainSelect, sql), "不允许 INTO"));
            }
            if (plainSelect.getForMode() != null) {
                violations.add(new RenderedQueryViolation(Math.max(0, scanner.findSequence("FOR")),
                    "不允许 FOR UPDATE / FOR SHARE"));
            }
        }
    }

    /**
     * 表引用：拒绝系统库；数据源无默认库时拒绝未限定库名的表。CTE 与 dual 不算表。
     *
     * @param tree 语法树收集结果
     * @param sql SQL
     * @param source 数据源事实
     * @param violations 拒绝原因收集
     */
    private static void inspectTables(SelectTreeInspector tree, String sql, QuerySource source,
        List<RenderedQueryViolation> violations) {
        for (Table table : tree.tables()) {
            String schema = table.getSchemaName() == null ? null : unquote(table.getSchemaName());
            String name = unquote(table.getName());
            if (schema == null && (tree.isCte(name) || DUAL.equalsIgnoreCase(name))) {
                continue;
            }
            if (schema == null && !source.hasDefaultDatabase()) {
                violations.add(new RenderedQueryViolation(offsetOf(table, sql),
                    "表 " + name + " 未限定库名：数据源未指定默认库，请写成 库.表"));
                continue;
            }
            String effective = schema == null ? source.getDefaultDatabase() : schema;
            if (source.dialect().isSystemSchema(effective)) {
                violations.add(new RenderedQueryViolation(offsetOf(table, sql),
                    "不允许引用系统库 " + effective.toLowerCase(Locale.ROOT) + " 中的表 " + name));
            }
        }
    }

    /**
     * 拒绝黑名单函数。
     *
     * @param tree 语法树收集结果
     * @param sql SQL
     * @param violations 拒绝原因收集
     */
    private static void inspectFunctions(SelectTreeInspector tree, String sql,
        List<RenderedQueryViolation> violations) {
        for (Function function : tree.functions()) {
            String name = function.getName() == null ? "" : function.getName();
            String simpleName = unquote(name.substring(name.lastIndexOf('.') + 1)).toUpperCase(Locale.ROOT);
            if (BLOCKED_FUNCTIONS.contains(simpleName)) {
                violations.add(new RenderedQueryViolation(offsetOf(function, sql), "不允许调用函数 " + simpleName));
            }
        }
    }

    /**
     * 去掉标识符两端的反引号或双引号。
     *
     * @param identifier 标识符
     * @return 去掉引号后的名称
     */
    static String unquote(String identifier) {
        String trimmed = identifier.strip();
        boolean quoted = trimmed.length() >= 2 && (trimmed.startsWith("`") && trimmed.endsWith("`")
            || trimmed.startsWith("\"") && trimmed.endsWith("\""));
        return quoted ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
    }

    /**
     * 语法树节点在 SQL 中的下标。
     *
     * @param node 节点
     * @param sql SQL
     * @return 下标；解析器未记录位置时为 0
     */
    private static int offsetOf(ASTNodeAccess node, String sql) {
        if (node.getASTNode() == null || node.getASTNode().jjtGetFirstToken() == null) {
            return 0;
        }
        Token token = node.getASTNode().jjtGetFirstToken();
        return offsetAt(sql, token.beginLine, token.beginColumn);
    }

    /**
     * 解析错误的下标。
     *
     * @param sql SQL
     * @param exception 解析异常
     * @return 出错单元的下标；无法确定时为 0
     */
    private static int parseErrorOffset(String sql, JSQLParserException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ParseException parseException && parseException.currentToken != null
                && parseException.currentToken.next != null) {
                Token token = parseException.currentToken.next;
                return offsetAt(sql, token.beginLine, token.beginColumn);
            }
        }
        return 0;
    }

    /**
     * 行列换算为下标。
     *
     * @param sql SQL
     * @param line 行号，从 1 开始
     * @param column 列号，从 1 开始
     * @return 下标，限制在 SQL 长度内
     */
    private static int offsetAt(String sql, int line, int column) {
        int offset = 0;
        for (int current = 1; current < line && offset < sql.length(); offset++) {
            if (sql.charAt(offset) == '\n') {
                current++;
            }
        }
        return Math.max(0, Math.min(sql.length(), offset + column - 1));
    }
}
