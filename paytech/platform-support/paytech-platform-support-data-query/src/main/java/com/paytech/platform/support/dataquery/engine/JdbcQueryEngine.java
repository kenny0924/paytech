package com.paytech.platform.support.dataquery.engine;

import java.nio.charset.StandardCharsets;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.platform.support.api.enums.DataQueryErrorCode;
import com.paytech.platform.support.dataquery.core.enums.DataSourceEngineType;
import com.paytech.platform.support.dataquery.domain.ConnectionCheckResult;
import com.paytech.platform.support.dataquery.domain.DataSourceConnectionConfig;
import com.paytech.platform.support.dataquery.template.JdbcValueBinder;
import com.paytech.platform.support.dataquery.template.RenderedQuery;
import com.paytech.platform.support.dataquery.template.RenderedQueryCheck;
import com.paytech.platform.support.dataquery.template.ValueBinder;

import net.sf.jsqlparser.statement.select.Select;

/**
 * JDBC 引擎（MySQL 协议，覆盖 MySQL 与 Doris）：值输出为 ? 加绑定值，渲染结果经只读校验后在语法树上追加分页。
 *
 * <p>执行：setQueryTimeout 超时由驱动发送 KILL QUERY；setMaxRows(limit+1)、setFetchSize(500)；
 * 累计响应字节超过上限时取消语句。值转换：整数、DECIMAL、浮点、日期时间与其他类型为字符串，布尔为 Boolean，
 * 二进制为 "[binary]"，NULL 为 null。</p>
 */
@Component
public class JdbcQueryEngine implements QueryEngine {

    /** 二进制列的占位文本。 */
    static final String BINARY = "[binary]";

    private static final int FETCH_SIZE = 500;

    /** 每个值的固定开销（分隔符与引号），用于估算响应字节。 */
    private static final int VALUE_OVERHEAD_BYTES = 3;

    private static final Set<String> UNAVAILABLE_STATES = Set.of("08", "28");

    private final ValueBinder valueBinder = new JdbcValueBinder();

    private final ReadOnlySqlGuard guard = new ReadOnlySqlGuard();

    private final SqlPagingRewriter rewriter = new SqlPagingRewriter();

    /** MYSQL 与 DORIS。 */
    @Override
    public Set<DataSourceEngineType> engineTypes() {
        return Set.of(DataSourceEngineType.MYSQL, DataSourceEngineType.DORIS);
    }

    /** 占位式绑定器。 */
    @Override
    public ValueBinder valueBinder() {
        return valueBinder;
    }

    /** 只读 SQL 校验。 */
    @Override
    public RenderedQueryCheck renderedQueryCheck(QuerySource source) {
        return guard.renderedQueryCheck(source);
    }

    /** 只读校验通过后改写分页。 */
    @Override
    public PreparedQuery prepare(RenderedQuery rendered, QuerySource source, QueryPaging paging) {
        Select select = guard.requireReadOnly(rendered.getText(), source);
        return rewriter.rewrite(select, rendered.getBindings(), paging);
    }

    /** 主查询后按需计数，二者共用超时预算。 */
    @Override
    public QueryResult execute(DataSourceHandle source, PreparedQuery query, QueryBudget budget) {
        long startedAt = System.nanoTime();
        try (Connection connection = source.connection()) {
            ResultAccumulator result = runMain(connection, query, budget);
            Long total = null;
            if (query.getCountSql() != null) {
                total = runCount(connection, query, remainingSeconds(budget, startedAt));
            }
            return new QueryResult(result.columns, result.rows, result.hasMore, total, elapsedMillis(startedAt));
        } catch (SQLTimeoutException timeout) {
            throw new FailException(DataQueryErrorCode.DATA_QUERY_TIMEOUT, DataQueryErrorCode.DATA_QUERY_TIMEOUT.getMessage()
                + "（上限 " + budget.getTimeoutSeconds() + " 秒）", timeout);
        } catch (SQLException failure) {
            throw translate(failure);
        }
    }

    /** 单个连接（不用连接池）执行 SELECT 1，再由方言检查授权；任何失败都作为结果返回，不含密码。 */
    @Override
    public ConnectionCheckResult check(DataSourceConnectionConfig config, String password) {
        try (Connection connection = DriverManager.getConnection(JdbcConnectionSettings.url(config),
            JdbcConnectionSettings.connectionProperties(config, password))) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT 1")) {
                rows.next();
            }
            return SqlDialect.forEngine(config.getEngineType()).inspectGrants(connection);
        } catch (SQLException failure) {
            return ConnectionCheckResult.unreachable(describe(failure));
        }
    }

    /**
     * 执行主查询并转换结果。
     *
     * @param connection 连接
     * @param query 查询
     * @param budget 预算
     * @return 结果
     * @throws SQLException 执行失败
     */
    ResultAccumulator runMain(Connection connection, PreparedQuery query, QueryBudget budget) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query.getSql(), ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY)) {
            statement.setQueryTimeout(budget.getTimeoutSeconds());
            statement.setMaxRows(query.getRowLimit() + 1);
            statement.setFetchSize(FETCH_SIZE);
            bind(statement, query.getBindings());
            try (ResultSet rows = statement.executeQuery()) {
                return read(statement, rows, query.getRowLimit(), budget.getMaxResponseBytes());
            }
        }
    }

    /**
     * 执行计数。
     *
     * @param connection 连接
     * @param query 查询
     * @param timeoutSeconds 剩余超时秒数
     * @return 总数
     * @throws SQLException 执行失败
     */
    long runCount(Connection connection, PreparedQuery query, int timeoutSeconds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query.getCountSql())) {
            statement.setQueryTimeout(timeoutSeconds);
            bind(statement, query.getCountBindings());
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    /**
     * 读取并转换行，多取一行判断 hasMore；累计字节超限时取消语句。
     *
     * @param statement 语句
     * @param rows 结果集
     * @param rowLimit 最多返回的行数
     * @param maxBytes 字节上限
     * @return 结果
     * @throws SQLException 读取失败
     */
    ResultAccumulator read(Statement statement, ResultSet rows, int rowLimit, long maxBytes) throws SQLException {
        ResultSetMetaData metaData = rows.getMetaData();
        ResultAccumulator result = new ResultAccumulator(columns(metaData));
        long bytes = 0;
        while (rows.next()) {
            if (result.rows.size() == rowLimit) {
                result.hasMore = true;
                break;
            }
            List<Object> row = new ArrayList<>(metaData.getColumnCount());
            for (int column = 1; column <= metaData.getColumnCount(); column++) {
                Object value = value(rows, column, metaData.getColumnType(column));
                bytes += estimateBytes(value);
                row.add(value);
            }
            if (bytes > maxBytes) {
                statement.cancel();
                throw new FailException(DataQueryErrorCode.DATA_QUERY_RESULT_TOO_LARGE);
            }
            result.rows.add(row);
        }
        return result;
    }

    /**
     * 列标签。
     *
     * @param metaData 元数据
     * @return 列名
     * @throws SQLException 读取失败
     */
    static List<String> columns(ResultSetMetaData metaData) throws SQLException {
        List<String> columns = new ArrayList<>(metaData.getColumnCount());
        for (int column = 1; column <= metaData.getColumnCount(); column++) {
            columns.add(metaData.getColumnLabel(column));
        }
        return columns;
    }

    /**
     * 按数据库类型转换一个值。
     *
     * @param rows 结果集
     * @param column 列序号
     * @param type JDBC 类型
     * @return String、Boolean 或 null
     * @throws SQLException 读取失败
     */
    static Object value(ResultSet rows, int column, int type) throws SQLException {
        Object value = switch (type) {
            case Types.BIT, Types.BOOLEAN -> rows.getBoolean(column);
            case Types.DECIMAL, Types.NUMERIC -> decimal(rows.getBigDecimal(column));
            case Types.DATE -> date(rows.getObject(column, LocalDate.class));
            case Types.TIMESTAMP -> dateTime(rows.getObject(column, LocalDateTime.class));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> rows.getObject(column) == null
                ? null : BINARY;
            default -> rows.getString(column);
        };
        return rows.wasNull() ? null : value;
    }

    /** DECIMAL 以不带指数的十进制字符串返回。 */
    static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    /** DATE 为 ISO-8601 日期。 */
    static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    /** DATETIME / TIMESTAMP 为不带时区的 ISO-8601，秒总是输出。 */
    static String dateTime(LocalDateTime value) {
        return value == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
    }

    /**
     * 估算值的 UTF-8 字节数。
     *
     * @param value 值
     * @return 字节数
     */
    static long estimateBytes(Object value) {
        if (value == null) {
            return 4 + VALUE_OVERHEAD_BYTES;
        }
        if (value instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8).length + VALUE_OVERHEAD_BYTES;
        }
        return 5 + VALUE_OVERHEAD_BYTES;
    }

    /**
     * 按占位符顺序绑定值。
     *
     * @param statement 语句
     * @param bindings 绑定值：String、Long、BigDecimal、Boolean、LocalDate、LocalDateTime 或 null
     * @throws SQLException 绑定失败
     */
    static void bind(PreparedStatement statement, List<Object> bindings) throws SQLException {
        for (int index = 0; index < bindings.size(); index++) {
            Object value = bindings.get(index);
            if (value == null) {
                statement.setNull(index + 1, Types.NULL);
            } else {
                statement.setObject(index + 1, value);
            }
        }
    }

    /** 剩余超时秒数，至少 1。 */
    static int remainingSeconds(QueryBudget budget, long startedAt) {
        long used = (System.nanoTime() - startedAt) / 1_000_000_000L;
        return (int) Math.max(1, budget.getTimeoutSeconds() - used);
    }

    /** 已用毫秒。 */
    static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /**
     * 连接类失败（SQLState 08、28，或取不到池连接）返回 503，其余 SQL 错误按业务拒绝返回数据库原因。
     *
     * @param failure SQL 异常
     * @return 项目异常
     */
    static RuntimeException translate(SQLException failure) {
        String state = failure.getSQLState();
        if (failure instanceof SQLTransientConnectionException
            || failure instanceof SQLNonTransientConnectionException
            || failure instanceof SQLRecoverableException
            || state != null && state.length() >= 2 && UNAVAILABLE_STATES.contains(state.substring(0, 2))) {
            return new ServiceUnavailableException(DataQueryErrorCode.DATA_SOURCE_UNAVAILABLE, failure);
        }
        return new BizCheckFailException("查询执行失败: " + describe(failure), failure);
    }

    /**
     * 安全的失败描述：SQLState 加驱动消息首行，不含密码。
     *
     * @param failure 异常
     * @return 描述
     */
    static String describe(SQLException failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName()
            : failure.getMessage().lines().findFirst().orElse("");
        return (failure.getSQLState() == null ? "" : "[" + failure.getSQLState() + "] ") + message;
    }

    /** 读取过程中的可变结果。 */
    static final class ResultAccumulator {

        /** 列名。 */
        final List<String> columns;

        /** 数据行。 */
        final List<List<Object>> rows = new ArrayList<>();

        /** 是否还有更多。 */
        boolean hasMore;

        /**
         * 以列名构造。
         *
         * @param columns 列名
         */
        ResultAccumulator(List<String> columns) {
            this.columns = columns;
        }
    }
}
