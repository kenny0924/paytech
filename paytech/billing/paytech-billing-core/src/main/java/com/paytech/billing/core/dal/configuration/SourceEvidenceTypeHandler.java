package com.paytech.billing.core.dal.configuration;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import com.paytech.foundation.common.error.ErrorException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * t_billing_fact_line.source_evidence（MySQL JSON 列）与 {@code Map<String, Object>} 之间的显式转换
 * <li>只在 Mapper XML 中按列显式引用，不全局注册 Map 类型；空值保持 SQL NULL。</li>
 * <li>数据库中不是 JSON 对象的值视为数据不变量被破坏，读取 Fail-Closed。</li>
 */
public final class SourceEvidenceTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {
    };

    /** 写入 JSON 文本。 */
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Map<String, Object> evidence,
        JdbcType jdbcType) throws SQLException {
        statement.setString(index, JSON.writeValueAsString(evidence));
    }

    /** 按列名读取。 */
    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    /** 按列序号读取。 */
    @Override
    public Map<String, Object> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    /** 存储过程输出参数读取。 */
    @Override
    public Map<String, Object> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    /** JSON 对象文本转 Map；为空时为空，非 JSON 对象为系统错误。 */
    static Map<String, Object> parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.readValue(json, OBJECT);
        } catch (JacksonException malformed) {
            throw new ErrorException("计费事实明细行计算证据不是 JSON 对象", malformed);
        }
    }
}
