package com.paytech.risk.test.builder.decision;

import java.time.Instant;

import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.DictionaryType;

/**
 * 字典当前行的测试 Builder
 * <li>{@link #reason()} 与 {@link #tag()} 提供合法默认值；编码缺省由 Mocker 随机生成。</li>
 * <li>不修复用例刻意设置的非法值，可直接构造首尾空白名称、零版本等损坏记录。</li>
 *
 * @author Kenny
 */
public final class DictionaryEntryBuilder {
    /** 默认创建时间，UTC 微秒精度。 */
    public static final Instant CREATE_TIME = Instant.parse("2026-04-05T06:07:08.123456Z");

    /** 默认最后更新时间，UTC 微秒精度。 */
    public static final Instant UPDATE_TIME = Instant.parse("2026-05-06T07:08:09.654321Z");

    /** 默认创建人。 */
    public static final long CREATED_BY = 70011L;

    /** 默认最后更新人。 */
    public static final long UPDATED_BY = 70012L;

    private final DictionaryType type;

    private String code;

    private String name;

    private String description;

    private String defaultMessage;

    private DictionaryStatus status = DictionaryStatus.ACTIVE;

    private long version = 1;

    private long createdBy = CREATED_BY;

    private long updatedBy = UPDATED_BY;

    private Instant createTime = CREATE_TIME;

    private Instant updateTime = UPDATE_TIME;

    private DictionaryEntryBuilder(DictionaryType type) {
        this.type = type;
    }

    /** 启用的拒绝原因，带说明和默认内部文案。 */
    public static DictionaryEntryBuilder reason() {
        return new DictionaryEntryBuilder(DictionaryType.REJECTION_REASON).name("Mock reason")
            .description("Mock reason description").defaultMessage("Mock default message");
    }

    /** 启用的主体标签，带说明；标签没有默认文案。 */
    public static DictionaryEntryBuilder tag() {
        return new DictionaryEntryBuilder(DictionaryType.SUBJECT_TAG).name("Mock tag").description("Mock tag description");
    }

    public DictionaryEntryBuilder code(String code) {
        this.code = code;
        return this;
    }

    public DictionaryEntryBuilder name(String name) {
        this.name = name;
        return this;
    }

    public DictionaryEntryBuilder description(String description) {
        this.description = description;
        return this;
    }

    public DictionaryEntryBuilder defaultMessage(String defaultMessage) {
        this.defaultMessage = defaultMessage;
        return this;
    }

    public DictionaryEntryBuilder status(DictionaryStatus status) {
        this.status = status;
        return this;
    }

    public DictionaryEntryBuilder version(long version) {
        this.version = version;
        return this;
    }

    public DictionaryEntryBuilder updatedBy(long updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }

    public DictionaryType type() {
        return type;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public DictionaryStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public long createdBy() {
        return createdBy;
    }

    public long updatedBy() {
        return updatedBy;
    }

    public Instant createTime() {
        return createTime;
    }

    public Instant updateTime() {
        return updateTime;
    }
}
