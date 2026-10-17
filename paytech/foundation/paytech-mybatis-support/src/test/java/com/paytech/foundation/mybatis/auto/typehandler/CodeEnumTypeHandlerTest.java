package com.paytech.foundation.mybatis.auto.typehandler;

import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link CodeEnumTypeHandler} JDBC contract tests.
 */
class CodeEnumTypeHandlerTest {

    @Test
    void test_mapsCodeAcrossJdbcBoundary() throws SQLException {
        CodeEnumTypeHandler<TestStatus> handler = new CodeEnumTypeHandler<>(TestStatus.class);
        AtomicInteger parameterIndex = new AtomicInteger();
        AtomicReference<String> persistedCode = new AtomicReference<>();
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> {
            if ("setString".equals(method)) {
                parameterIndex.set((Integer) arguments[0]);
                persistedCode.set((String) arguments[1]);
            }
            return null;
        });

        handler.setNonNullParameter(statement, 3, TestStatus.ACTIVE, JdbcType.VARCHAR);

        Assertions.assertAll(
            () -> Assertions.assertEquals(3, parameterIndex.get()),
            () -> Assertions.assertEquals("ACTIVE", persistedCode.get()),
            () -> Assertions.assertEquals(
                TestStatus.ACTIVE,
                handler.getNullableResult(stringResultSet("ACTIVE"), "status")
            ),
            () -> Assertions.assertEquals(
                TestStatus.DISABLED,
                handler.getNullableResult(stringResultSet("DISABLED"), 1)
            ),
            () -> Assertions.assertEquals(
                TestStatus.ACTIVE,
                handler.getNullableResult(stringCallableStatement("ACTIVE"), 1)
            )
        );
    }

    @Test
    void test_sqlNullRemainsNull() throws SQLException {
        CodeEnumTypeHandler<TestStatus> handler = new CodeEnumTypeHandler<>(TestStatus.class);

        Assertions.assertAll(
            () -> Assertions.assertNull(handler.getNullableResult(stringResultSet(null), "status")),
            () -> Assertions.assertNull(handler.getNullableResult(stringResultSet(null), 1)),
            () -> Assertions.assertNull(handler.getNullableResult(stringCallableStatement(null), 1))
        );
    }

    @Test
    void test_unknownPersistedCodeFailsClosed() {
        CodeEnumTypeHandler<TestStatus> handler = new CodeEnumTypeHandler<>(TestStatus.class);

        SQLException exception = Assertions.assertThrows(
            SQLException.class,
            () -> handler.getNullableResult(stringResultSet("UNKNOWN"), "status")
        );

        Assertions.assertAll(
            () -> Assertions.assertTrue(exception.getMessage().contains("UNKNOWN")),
            () -> Assertions.assertTrue(exception.getMessage().contains(TestStatus.class.getName()))
        );
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void test_invalidEnumDefinitionsAreRejected() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new CodeEnumTypeHandler<>(DuplicateCode.class)
            ),
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new CodeEnumTypeHandler<>(NullCode.class)
            ),
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new CodeEnumTypeHandler((Class) PlainStatus.class)
            )
        );
    }

    private ResultSet stringResultSet(String code) {
        return proxy(ResultSet.class, (method, arguments) -> "getString".equals(method) ? code : null);
    }

    private CallableStatement stringCallableStatement(String code) {
        return proxy(
            CallableStatement.class,
            (method, arguments) -> "getString".equals(method) ? code : null
        );
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

    private enum TestStatus implements CodeEnum {
        ACTIVE("ACTIVE"),
        DISABLED("DISABLED");

        private final String code;

        TestStatus(String code) {
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }
    }

    private enum DuplicateCode implements CodeEnum {
        FIRST("DUPLICATE"),
        SECOND("DUPLICATE");

        private final String code;

        DuplicateCode(String code) {
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }
    }

    private enum NullCode implements CodeEnum {
        VALUE;

        @Override
        public String getCode() {
            return null;
        }
    }

    private enum PlainStatus {
        ACTIVE
    }
}
