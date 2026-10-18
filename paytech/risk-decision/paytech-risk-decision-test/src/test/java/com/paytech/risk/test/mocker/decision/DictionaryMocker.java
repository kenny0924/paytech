package com.paytech.risk.test.mocker.decision;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.DictionaryType;
import com.paytech.risk.test.builder.decision.DictionaryEntryBuilder;
import com.paytech.risk.test.mapper.decision.DictionaryTestMapper;

/**
 * 拒绝原因与主体标签的本地数据 Mocker
 * <li>插入的行及交给被测写接口创建的编码都先登记，Clear 只按这些编码删除。</li>
 * <li>编码以 MOCK_RR_ / MOCK_ST_ 加 15 位随机数生成，与运营目录和其他测试隔离。</li>
 *
 * @author Kenny
 */
@TestComponent
public class DictionaryMocker implements Clear {

    private final DictionaryTestMapper mapper;

    private final Set<String> reasonCodes = new LinkedHashSet<>();

    private final Set<String> tagCodes = new LinkedHashSet<>();

    public DictionaryMocker(DictionaryTestMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 字典当前事实；同时作为数据库与响应断言的期望值
     * <li>id 为 0 表示由被测接口生成、期望中不约束具体值。</li>
     */
    public record MockedDictionary(DictionaryType type, long id, String code, String name, String description,
        String defaultMessage, DictionaryStatus status, long version, long createdBy, long updatedBy,
        Instant createTime, Instant updateTime) {

        /** 创建接口成功后的期望：启用、修订号 1，创建与更新审计一致。 */
        public static MockedDictionary created(DictionaryType type, String code, String name, String description,
            String defaultMessage, long actor, Instant time) {
            return new MockedDictionary(type, 0, code, name, description, defaultMessage, DictionaryStatus.ACTIVE, 1,
                actor, actor, time, time);
        }

        /** 一次成功变更后的期望：修订号加一，保留编码、主键、创建人与创建时间。 */
        public MockedDictionary changed(String name, String description, String defaultMessage,
            DictionaryStatus status, long actor, Instant time) {
            return new MockedDictionary(type, id, code, name, description, defaultMessage, status, version + 1,
                createdBy, actor, createTime, time);
        }

        /** 仅改变状态的期望，内容保持不变。 */
        public MockedDictionary statusChanged(DictionaryStatus status, long actor, Instant time) {
            return changed(name, description, defaultMessage, status, actor, time);
        }
    }

    /** 插入一条字典当前行；未指定编码时随机生成。 */
    public MockedDictionary mock(DictionaryEntryBuilder builder) {
        String code = builder.code() == null ? newCode(builder.type()) : own(builder.type(), builder.code());
        long id = randomId();
        if (builder.type() == DictionaryType.REJECTION_REASON) {
            mapper.insertReason(id, code, builder.name(), builder.description(), builder.defaultMessage(),
                builder.status(), builder.version(), builder.createdBy(), builder.updatedBy(), builder.createTime(),
                builder.updateTime());
        } else {
            mapper.insertTag(id, code, builder.name(), builder.description(), builder.status(), builder.version(),
                builder.createdBy(), builder.updatedBy(), builder.createTime(), builder.updateTime());
        }
        return new MockedDictionary(builder.type(), id, code, builder.name(), builder.description(),
            builder.defaultMessage(), builder.status(), builder.version(), builder.createdBy(), builder.updatedBy(),
            builder.createTime(), builder.updateTime());
    }

    /** 生成并登记一个未使用的随机编码，供被测接口创建或作为列表关键词前缀。 */
    public String newCode(DictionaryType type) {
        return own(type, (type == DictionaryType.REJECTION_REASON ? "MOCK_RR_" : "MOCK_ST_") + randomId());
    }

    /** 登记由用例指定、可能被被测接口写入的编码，结束时删除。 */
    public String own(DictionaryType type, String code) {
        (type == DictionaryType.REJECTION_REASON ? reasonCodes : tagCodes).add(code);
        return code;
    }

    /** 删除本 Mocker 登记的全部编码；删除失败时保留登记，下一次清理继续重试。 */
    @Override
    public void clear() {
        reasonCodes.forEach(code -> mapper.delete(DictionaryType.REJECTION_REASON, code));
        tagCodes.forEach(code -> mapper.delete(DictionaryType.SUBJECT_TAG, code));
        reasonCodes.clear();
        tagCodes.clear();
    }

    /** 15 位正整数，远离 Snowflake 生成的生产 ID 区间。 */
    private static long randomId() {
        return ThreadLocalRandom.current().nextLong(100_000_000_000_000L, 900_000_000_000_000L);
    }
}
