package com.paytech.risk.decision.core.dal.dataobject;

import static com.paytech.risk.decision.domainservice.validator.dictionary.DictionaryTextValidator.requireCode;
import static com.paytech.risk.decision.domainservice.validator.dictionary.DictionaryTextValidator.requireText;

import java.time.Instant;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException.Reason;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 主体标签的持久化当前事实，限 Decision Owner JAR 内部使用。 */
@Data
@AllArgsConstructor
public class RuleSubjectTag {

    /**
     * Risk Snowflake 主键。
     */
    private final long tagId;

    /**
     * 永久唯一标签编码。
     */
    private final String tagCode;

    /**
     * 当前名称。
     * <li>不代表历史名称，1–100 个 Unicode 码点，不允许首尾空白。</li>
     */
    private String tagName;

    /**
     * 内部说明。
     * <li>可空，最多 500 个 Unicode 码点，可保留换行。</li>
     */
    private String description;

    /**
     * 字典选择资格。
     * <li>ACTIVE 或 DISABLED，不改变已有规则。</li>
     */
    private DictionaryStatus status;

    /**
     * 并发版本。
     * <li>成功更新加一。</li>
     */
    private long version;

    /**
     * 可信 Operations 创建账号 ID。
     */
    private final long createdBy;

    /**
     * 可信 Operations 最后修改账号 ID。
     */
    private long updatedBy;

    /**
     * 创建时间。
     * <li>UTC，微秒精度。</li>
     */
    private final Instant createTime;

    /**
     * 最后修改时间。
     * <li>UTC，微秒精度。</li>
     */
    private Instant updateTime;

    /** 根据已验证输入创建启用的初始事实，空说明归一为 null，创建和更新审计信息保持一致。 */
    public static RuleSubjectTag create(long id, String code, String name, String description,
        long actorId, Instant time) {
        return new RuleSubjectTag(id, code, name, description != null && description.isEmpty() ? null : description,
            DictionaryStatus.ACTIVE, 1, actorId, actorId, time, time);
    }

    /** 校验已加载修订号，过期请求拒绝更新，版本上限作为系统错误。 */
    public void verifyExpectedVersion(long expectedVersion) {
        if (expectedVersion <= 0 || version != expectedVersion) {
            throw new DictionaryConflictException(Reason.CHANGED);
        }
        if (version == Long.MAX_VALUE) {
            throw new ErrorException("字典版本已达上限");
        }
    }

    /** SQL 成功后同步当前实例，保留代码、创建人与创建时间。 */
    public void applyChange(String name, String description, DictionaryStatus status,
        long actorId, Instant time) {
        this.tagName = name;
        this.description = description;
        this.status = status;
        this.version++;
        this.updatedBy = actorId;
        this.updateTime = time;
    }

    /** 校验当前记录的元数据及内容，损坏数据以系统错误拒绝，不改写保存事实。 */
    public void verifyIntegrity() {
        if (status == null || version <= 0 || createdBy <= 0 || updatedBy <= 0 || createTime == null
            || updateTime == null || createTime.getNano() % 1000 != 0 || updateTime.getNano() % 1000 != 0) {
            throw new ErrorException("字典当前记录损坏");
        }
        try {
            requireCode(tagCode);
            requireText(tagName, 100, false, false);
            if (description != null) {
                requireText(description, 500, true, true);
            }
        } catch (InvalidParameterException failure) {
            throw new ErrorException("字典当前内容损坏", failure);
        }
    }
}
