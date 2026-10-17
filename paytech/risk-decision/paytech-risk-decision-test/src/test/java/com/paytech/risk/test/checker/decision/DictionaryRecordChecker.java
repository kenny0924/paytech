package com.paytech.risk.test.checker.decision;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.risk.decision.domain.enums.DictionaryType;
import com.paytech.risk.test.mapper.decision.DictionaryTestMapper;
import com.paytech.risk.test.mapper.decision.DictionaryTestMapper.DictionaryRow;
import com.paytech.risk.test.mocker.decision.DictionaryMocker.MockedDictionary;

/**
 * 字典当前行的数据库 Checker
 * <li>从真实库读取并逐列核对；被拒绝的写入用原夹具断言未改变，或断言编码未落库。</li>
 *
 * @author Kenny
 */
@TestComponent
public class DictionaryRecordChecker {

    private final DictionaryTestMapper mapper;

    public DictionaryRecordChecker(DictionaryTestMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前行与期望完全一致，包括主键；拒绝写入时传入原夹具即断言未改变。 */
    public void assertStored(MockedDictionary expected) {
        DictionaryRow row = load(expected);
        assertEquals(expected.id(), row.id(), "主键不符");
        assertColumns(expected, row);
    }

    /** 被测接口新建的行：主键由服务端生成，其余列与期望一致。 */
    public void assertCreated(MockedDictionary expected) {
        DictionaryRow row = load(expected);
        assertTrue(row.id() > 0, "主键必须为正数");
        assertColumns(expected, row);
    }

    /** 该类型下不存在此编码。 */
    public void assertAbsent(DictionaryType type, String code) {
        assertTrue(mapper.find(type, code).isEmpty(), "不应写入字典：" + code);
    }

    private DictionaryRow load(MockedDictionary expected) {
        return mapper.find(expected.type(), expected.code())
            .orElseThrow(() -> new AssertionError("字典未落库：" + expected.code()));
    }

    private static void assertColumns(MockedDictionary expected, DictionaryRow row) {
        assertAll("字典行 " + expected.code(), () -> assertEquals(expected.code(), row.code()),
            () -> assertEquals(expected.name(), row.name(), "名称"),
            () -> assertEquals(expected.description(), row.description(), "说明"),
            () -> assertEquals(expected.defaultMessage(), row.defaultMessage(), "默认文案"),
            () -> assertEquals(expected.status().getCode(), row.status(), "状态"),
            () -> assertEquals(expected.version(), row.version(), "修订号"),
            () -> assertEquals(expected.createdBy(), row.createdBy(), "创建人"),
            () -> assertEquals(expected.updatedBy(), row.updatedBy(), "更新人"),
            () -> assertEquals(expected.createTime(), row.createTime(), "创建时间"),
            () -> assertEquals(expected.updateTime(), row.updateTime(), "更新时间"));
    }
}
