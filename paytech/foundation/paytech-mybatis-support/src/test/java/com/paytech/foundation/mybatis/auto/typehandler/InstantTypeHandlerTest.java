package com.paytech.foundation.mybatis.auto.typehandler;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link InstantTypeHandler} UTC JDBC contract tests.
 */
class InstantTypeHandlerTest {

    private static final Instant INSTANT = Instant.parse("2026-09-03T05:30:12.123456Z");

    @Test
    void test_mapsInstantUsingUtcCalendar() throws SQLException {
        InstantTypeHandler handler = new InstantTypeHandler();
        AtomicInteger parameterIndex = new AtomicInteger();
        AtomicReference<Timestamp> timestamp = new AtomicReference<>();
        AtomicReference<Calendar> calendar = new AtomicReference<>();
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> {
            if ("setTimestamp".equals(method)) {
                parameterIndex.set((Integer) arguments[0]);
                timestamp.set((Timestamp) arguments[1]);
                calendar.set((Calendar) arguments[2]);
            }
            return null;
        });

        handler.setNonNullParameter(statement, 2, INSTANT, JdbcType.TIMESTAMP);

        Assertions.assertAll(
            () -> Assertions.assertEquals(2, parameterIndex.get()),
            () -> Assertions.assertEquals(Timestamp.from(INSTANT), timestamp.get()),
            () -> Assertions.assertEquals("UTC", calendar.get().getTimeZone().getID()),
            () -> Assertions.assertEquals(INSTANT, handler.getNullableResult(resultSet(INSTANT), "createdAt")),
            () -> Assertions.assertEquals(INSTANT, handler.getNullableResult(resultSet(INSTANT), 1)),
            () -> Assertions.assertEquals(INSTANT, handler.getNullableResult(callableStatement(INSTANT), 1))
        );
    }

    @Test
    void test_sqlNullRemainsNull() throws SQLException {
        InstantTypeHandler handler = new InstantTypeHandler();

        Assertions.assertAll(
            () -> Assertions.assertNull(handler.getNullableResult(resultSet(null), "createdAt")),
            () -> Assertions.assertNull(handler.getNullableResult(resultSet(null), 1)),
            () -> Assertions.assertNull(handler.getNullableResult(callableStatement(null), 1))
        );
    }

    private ResultSet resultSet(Instant value) {
        return proxy(ResultSet.class, (method, arguments) -> {
            assertUtcCalendar(method, arguments);
            return "getTimestamp".equals(method) && value != null ? Timestamp.from(value) : null;
        });
    }

    private CallableStatement callableStatement(Instant value) {
        return proxy(CallableStatement.class, (method, arguments) -> {
            assertUtcCalendar(method, arguments);
            return "getTimestamp".equals(method) && value != null ? Timestamp.from(value) : null;
        });
    }

    private void assertUtcCalendar(String method, Object[] arguments) {
        if (!"getTimestamp".equals(method)) {
            return;
        }
        Calendar calendar = (Calendar) arguments[1];
        Assertions.assertEquals("UTC", calendar.getTimeZone().getID());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> contract, JdbcInvocation invocation) {
        return (T) Proxy.newProxyInstance(
            contract.getClassLoader(),
            new Class<?>[]{contract},
            (proxy, method, arguments) -> invocation.invoke(method.getName(), arguments)
        );
    }

    @FunctionalInterface
    private interface JdbcInvocation {

        Object invoke(String method, Object[] arguments) throws Throwable;
    }
}
