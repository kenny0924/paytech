package com.paytech.platform.support.test.dataquery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;
import com.paytech.platform.support.dataquery.core.enums.QueryPagingMode;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.dal.DataQueryTestMapper;

import lombok.RequiredArgsConstructor;

/**
 * 数据查询的本地前置数据：直接插入数据源、模板与版本 1，并为数据源密码登记 Vault DTO；不调用被测接口。
 * <li>插入的行由 PlatformDataCleaner 整表回收，Vault 配置由 {@link DataSourcePasswordVaultMock} 每例清理。</li>
 */
@TestComponent
@RequiredArgsConstructor
public class DataQueryMocker {

    /** 直接插入的数据源使用的 Token 序号起点，与 Protect 生成的序号区分。 */
    private static final int SEEDED_TOKEN_BASE = 900_000;

    private final DataQueryFixture fixture;
    private final DataQueryTestMapper mapper;
    private final DataSourcePasswordVaultMock vault;
    private int seeded;

    /**
     * 以只读账号、默认库 mor_data_query_fixture 插入 ACTIVE 数据源。
     *
     * @param code 数据源编码
     * @return 数据源 ID
     */
    public long activeReaderSource(String code) {
        return source(code, fixture.readerUsername(), fixture.readerPassword(), DataQueryFixture.DATABASE,
            DataSourceStatus.ACTIVE);
    }

    /**
     * 插入数据源。
     *
     * @param code 编码
     * @param username 账号
     * @param password 密码，登记到 Vault Mock
     * @param database 默认库，可为 null
     * @param status 状态
     * @return 数据源 ID
     */
    public synchronized long source(String code, String username, String password, String database,
        DataSourceStatus status) {
        long id = OperatorBuilder.nextId();
        String token = DataSourcePasswordVaultMock.token(SEEDED_TOKEN_BASE + ++seeded);
        vault.allow(token, password);
        assertEquals(1, mapper.insertDataSource(id, code, fixture.host(), fixture.port(), database, username, token,
            status.getCode(), now()));
        return id;
    }

    /**
     * 插入模板与版本 1。
     *
     * @param code 模板编码
     * @param dataSourceId 数据源 ID
     * @param body 正文
     * @param parameters 参数声明 JSON 数组
     * @param columns 列声明 JSON 数组，可为 null
     * @param constraints 约束 JSON 对象
     * @param status 状态
     * @return 模板 ID
     */
    public long template(String code, long dataSourceId, String body, String parameters, String columns,
        String constraints, QueryTemplateStatus status) {
        long id = OperatorBuilder.nextId();
        Instant now = now();
        assertEquals(1, mapper.insertTemplate(id, code, dataSourceId, status.getCode(), now));
        assertEquals(1, mapper.insertVersion(OperatorBuilder.nextId(), id, dataSourceId, body, parameters, columns,
            constraints, now));
        return id;
    }

    /**
     * 约束 JSON，与生产写入的结构一致。
     *
     * @param maxRows 最大行数
     * @param timeoutSeconds 超时秒数
     * @param mode 分页方式
     * @param maxPageSize 页大小上限，NONE 为 null
     * @param countAllowed 是否允许总数
     * @param cursorColumns 游标列
     * @return JSON
     */
    public static String constraints(int maxRows, int timeoutSeconds, QueryPagingMode mode, Integer maxPageSize,
        boolean countAllowed, String... cursorColumns) {
        String cursor = Arrays.stream(cursorColumns).map(column -> "\"" + column + "\"")
            .collect(Collectors.joining(","));
        return "{\"maxRows\":" + maxRows + ",\"timeoutSeconds\":" + timeoutSeconds + ",\"pagingMode\":\""
            + mode.getCode() + "\"" + (maxPageSize == null ? "" : ",\"maxPageSize\":" + maxPageSize)
            + ",\"countAllowed\":" + countAllowed + ",\"cursorColumns\":[" + cursor + "]}";
    }

    /** 与 MySQL DATETIME(6) 一致的 UTC 微秒时间。 */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
