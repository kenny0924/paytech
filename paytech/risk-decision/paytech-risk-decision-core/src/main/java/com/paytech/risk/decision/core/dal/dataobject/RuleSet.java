package com.paytech.risk.decision.core.dal.dataobject;

import java.time.Instant;
import java.util.regex.Pattern;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

/** 规则集整体管理根；删除只改变此根，不改写历史版本。 */
@Data
@AllArgsConstructor
public class RuleSet {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    /**
     * 规则集稳定身份。
     * <li>由 Snowflake 生成，与版本 ID 独立。</li>
     */
    private long ruleSetId;

    /**
     * 所属决策点。
     * <li>创建后不可改变。</li>
     */
    private long decisionPointId;

    /**
     * 规则集编码。
     * <li>在决策点内永久保留，删除后不复用。</li>
     */
    @NonNull
    private String ruleSetCode;

    /**
     * 当前规则集版本 ID。
     * <li>指向同一规则集最高业务版本，删除后仍保留。</li>
     */
    private long currentRuleSetVersionId;

    /**
     * 整体管理状态。
     * <li>表示是否还可管理，不代表发布生效状态。</li>
     */
    @NonNull
    private RuleSetManagementStatus status;

    /**
     * 管理 CAS 版本。
     * <li>保存、首次冻结、删除分别递增。</li>
     */
    private long version;

    /**
     * 删除操作者。
     * <li>仅 DELETED 必填，删除后不可改变；可管理时为空。</li>
     */
    private Long deletedBy;

    /**
     * 删除时间。
     * <li>UTC 微秒时间，仅 DELETED 必填；可管理时为空。</li>
     */
    private Instant deletedTime;

    /**
     * 删除原因。
     * <li>删除时固定为 OPERATOR_REQUEST，可管理时为空。</li>
     */
    private String deleteReasonCode;

    /**
     * 删除事件摘要。
     * <li>域分隔 SHA-256，新删除由永久根身份派生，历史摘要保留；可管理时为空。</li>
     */
    private String deleteTriggerId;

    /**
     * 首个配置创建时间。
     * <li>UTC 微秒时间。</li>
     */
    @NonNull
    private Instant createTime;

    /**
     * 最后管理变更时间。
     * <li>UTC 微秒时间，重放不更新。</li>
     */
    @NonNull
    private Instant updateTime;

    /** 校验已加载管理根自身的完整性及删除证据，不查询关联对象或改变状态。 */
    public void verifyIntegrity() {
        if (ruleSetId <= 0 || decisionPointId <= 0 || currentRuleSetVersionId <= 0 || version <= 0
            || !CODE.matcher(ruleSetCode).matches()) {
            throw new ErrorException("规则集管理根损坏");
        }
        if (status == RuleSetManagementStatus.CONFIGURABLE) {
            if (deletedBy != null || deletedTime != null || deleteReasonCode != null || deleteTriggerId != null) {
                throw new ErrorException("未删除规则集包含删除证据");
            }
        } else if (deletedBy == null || deletedBy <= 0 || deletedTime == null
            || !"OPERATOR_REQUEST".equals(deleteReasonCode) || deleteTriggerId == null
            || !HASH.matcher(deleteTriggerId).matches() || !deletedTime.equals(updateTime)) {
            throw new ErrorException("规则集删除证据缺失或损坏");
        }
    }

    /**
     * 校验当前配置指针、不可变命名空间及头版本状态的一致性。
     * 
     * @param current
     *            调用方已加载的当前业务版本，缺失时视为持久化事实损坏
     */
    public void verifyManagementHead(RuleSetVersion current) {
        verifyIntegrity();
        if (current == null || currentRuleSetVersionId != current.ruleSetVersionId()
            || decisionPointId != current.decisionPointId() || !ruleSetCode.equals(current.ruleSetCode())
            || (current.status() != RuleSetVersionStatus.DRAFT && current.status() != RuleSetVersionStatus.FROZEN)) {
            throw new ErrorException("规则集当前配置指针或版本状态损坏");
        }
    }

    /** 判断已加载版本是否属于本规则集的不可变命名空间，不限制其是否为当前版本。 */
    public boolean ownsVersion(RuleSetVersion candidate) {
        return candidate != null && decisionPointId == candidate.decisionPointId()
            && ruleSetCode.equals(candidate.ruleSetCode());
    }

    /** 整体管理 CAS 不匹配时保留调用者原预期，要求重新确认。 */
    public void verifyExpectedVersion(long expectedVersion) {
        if (version != expectedVersion)
            throw new com.paytech.risk.decision.domainservice.service.RuleSetManagementConflictException(
                com.paytech.risk.decision.domainservice.service.RuleSetManagementConflictException.Reason.CHANGED,
                "规则集配置已变化，请刷新后重新确认删除");
    }

    /** 删除事件以永久根身份派生，不依赖 HTTP Key，也不改变既有删除证据。 */
    public String deletionTriggerId() {
        try {
            byte[] source = ("RISK_RULE_SET_DELETE:" + ruleSetId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new ErrorException("SHA-256 不可用", exception);
        }
    }
}
