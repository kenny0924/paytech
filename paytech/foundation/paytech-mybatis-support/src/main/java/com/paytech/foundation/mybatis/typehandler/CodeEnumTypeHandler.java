package com.paytech.foundation.mybatis.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.foundation.common.enums.CodeEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * 使用稳定业务 Code 映射 MyBatis 内部枚举字段。
 *
 * @param <E> 同时实现 {@link CodeEnum} 的枚举类型
 */
public final class CodeEnumTypeHandler<E extends Enum<E> & CodeEnum> extends BaseTypeHandler<E> {

    private final Class<E> enumType;
    private final Map<String, E> valuesByCode;

    /**
     * 创建指定枚举类型的处理器。
     *
     * @param enumType 枚举类型
     */
    public CodeEnumTypeHandler(Class<E> enumType) {
        this.enumType = validateType(enumType);
        this.valuesByCode = indexValues(enumType);
    }

    @Override
    public void setNonNullParameter(
        PreparedStatement statement,
        int parameterIndex,
        E parameter,
        JdbcType jdbcType
    ) throws SQLException {
        statement.setString(parameterIndex, parameter.getCode());
    }

    @Override
    public E getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return valueOf(resultSet.getString(columnName));
    }

    @Override
    public E getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return valueOf(resultSet.getString(columnIndex));
    }

    @Override
    public E getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return valueOf(statement.getString(columnIndex));
    }

    private Class<E> validateType(Class<E> type) {
        if (type == null || !type.isEnum() || !CodeEnum.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("MyBatis 枚举类型必须实现 CodeEnum");
        }
        return type;
    }

    private Map<String, E> indexValues(Class<E> type) {
        Map<String, E> indexedValues = new LinkedHashMap<>();
        for (E value : type.getEnumConstants()) {
            String code = value.getCode();
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("枚举 Code 不能为空: " + type.getName());
            }
            E previous = indexedValues.putIfAbsent(code, value);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "枚举 Code 重复: " + type.getName() + ", code=" + code
                );
            }
        }
        return Map.copyOf(indexedValues);
    }

    private E valueOf(String code) throws SQLException {
        if (code == null) {
            return null;
        }
        E value = valuesByCode.get(code);
        if (value == null) {
            throw new SQLException("未知枚举 Code: " + enumType.getName() + ", code=" + code);
        }
        return value;
    }
}
