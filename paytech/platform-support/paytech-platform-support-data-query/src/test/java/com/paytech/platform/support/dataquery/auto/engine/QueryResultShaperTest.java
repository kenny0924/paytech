package com.paytech.platform.support.dataquery.auto.engine;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.dataquery.core.enums.ColumnMaskType;
import com.paytech.platform.support.dataquery.domain.QueryColumnRule;
import com.paytech.platform.support.dataquery.domain.QueryCursorCodec;
import com.paytech.platform.support.dataquery.engine.QueryResult;
import com.paytech.platform.support.dataquery.engine.QueryResultShaper;

/**
 * 结果屏蔽与游标（technique 4.4）：纯 Java 公开合同。
 *
 * @see QueryResultShaper#maskRow(List, List)
 * @see QueryResultShaper#nextCursor(QueryResult, List, int)
 * @see QueryCursorCodec#decode(String, int)
 */
class QueryResultShaperTest {

    /** HIDE 去掉列，FULL 全部掩码，KEEP_EDGES 保留首尾；列名不区分大小写；null 不屏蔽。 */
    @Test
    void test_masking() {
        List<QueryColumnRule> rules = QueryResultShaper.rulesFor(List.of("EMAIL", "phone", "secret", "note"), List.of(
            new QueryColumnRule("email", "邮箱", ColumnMaskType.KEEP_EDGES, 2, 4),
            new QueryColumnRule("phone", null, ColumnMaskType.FULL, 0, 0),
            new QueryColumnRule("secret", null, ColumnMaskType.HIDE, 0, 0)));

        assertAll(() -> assertEquals(List.of("ab****.com", "******", "free"),
                QueryResultShaper.maskRow(List.of("ab@x.y.com", "123456", "s3cr3t", "free"), rules)),
            () -> assertEquals(Arrays.asList("a", null, "x"),
                QueryResultShaper.maskRow(Arrays.asList("a", null, "s", "x"), rules)));
    }

    /** 有更多行时以最后一行的游标列生成游标，带版本号；版本不一致与格式错误被拒绝。 */
    @Test
    void test_cursorRoundTrip() {
        QueryResult result = new QueryResult(List.of("refund_id", "create_time"),
            List.of(List.of("2003", "2026-10-07T09:00:00"), List.of("2004", "2026-10-08T09:00:00")), true, null, 1);
        String cursor = QueryResultShaper.nextCursor(result, List.of("refund_id", "create_time"), 3);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("refund_id", "2004");
        expected.put("create_time", "2026-10-08T09:00:00");
        assertAll(() -> assertEquals(expected, QueryCursorCodec.decode(cursor, 3)),
            () -> assertThrows(BizCheckFailException.class, () -> QueryCursorCodec.decode(cursor, 4)),
            () -> assertThrows(InvalidParameterException.class, () -> QueryCursorCodec.decode("not-a-cursor!", 3)),
            () -> assertNull(QueryResultShaper.nextCursor(new QueryResult(result.getColumns(), result.getRows(), false,
                null, 1), List.of("refund_id"), 3)),
            () -> assertThrows(BizCheckFailException.class,
                () -> QueryResultShaper.nextCursor(result, List.of("missing"), 3)));
    }
}
