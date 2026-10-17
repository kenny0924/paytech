package com.paytech.foundation.mybatis.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * 在 MySQL {@code DATETIME(6)} 与 UTC {@link Instant} 之间显式转换。
 */
@MappedTypes(Instant.class)
@MappedJdbcTypes(value = JdbcType.TIMESTAMP, includeNullJdbcType = true)
public final class InstantTypeHandler extends BaseTypeHandler<Instant> {

    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");

    @Override
    public void setNonNullParameter(
        PreparedStatement statement,
        int parameterIndex,
        Instant parameter,
        JdbcType jdbcType
    ) throws SQLException {
        statement.setTimestamp(parameterIndex, Timestamp.from(parameter), utcCalendar());
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return toInstant(resultSet.getTimestamp(columnName, utcCalendar()));
    }

    @Override
    public Instant getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return toInstant(resultSet.getTimestamp(columnIndex, utcCalendar()));
    }

    @Override
    public Instant getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return toInstant(statement.getTimestamp(columnIndex, utcCalendar()));
    }

    private Calendar utcCalendar() {
        return Calendar.getInstance(UTC_TIME_ZONE);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
