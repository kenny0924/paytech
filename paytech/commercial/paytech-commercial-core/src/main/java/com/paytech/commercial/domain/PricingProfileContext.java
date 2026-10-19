package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.enums.PricingProfileKind;
import com.paytech.commercial.core.dal.dataobject.BusinessChangeRecord;
import com.paytech.commercial.core.dal.dataobject.PricingProfile;
import com.paytech.commercial.core.dal.dataobject.PricingProfileCond;
import com.paytech.commercial.domain.enums.PricingProfileStatus;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.Getter;
import lombok.Setter;

/**
 * 定价画像一次写入命令的上下文：贯穿画像写入 Processor 与 PricingProfileService（technique.md §2.2）
 * <li>Processor 校验后一次构造；Service 按其中已准备的行与目标值保存。root 为画像 BCR Root（创建时由 Service 回填）。</li>
 * <li>种类与条件维度相容规则（database.md §5.6）由 {@link #checkDefinition()} 表达，只依赖已持有的数据。</li>
 */
@Getter
@Setter
public class PricingProfileContext {
    /** 各种类允许的条件维度；PERIODIC 为周期或一次性事件，无画像维度。 */
    static final Map<PricingProfileKind, Set<PricingDimension>> KIND_DIMENSIONS = Map.of(
        PricingProfileKind.PAYMENT_METHOD, Set.of(PricingDimension.CARD_BRAND, PricingDimension.FUNDING_TYPE,
            PricingDimension.CARD_LEVEL, PricingDimension.CARD_PRODUCT, PricingDimension.ISSUER_REGION,
            PricingDimension.ISSUER_COUNTRY, PricingDimension.THREE_DS_RESULT, PricingDimension.CURRENCY,
            PricingDimension.CONSUMER_COUNTRY, PricingDimension.TRANSACTION_TYPE),
        PricingProfileKind.PAYOUT_RAIL, Set.of(PricingDimension.PAYOUT_RAIL, PricingDimension.DESTINATION_COUNTRY,
            PricingDimension.CURRENCY),
        PricingProfileKind.FX_PAIR, Set.of(PricingDimension.CURRENCY_PAIR),
        PricingProfileKind.DISPUTE, Set.of(PricingDimension.DISPUTE_STAGE, PricingDimension.CARD_BRAND,
            PricingDimension.CURRENCY),
        PricingProfileKind.PERIODIC, Set.of());

    /** 画像；创建时为待插入的新行 */
    private final PricingProfile profile;

    /** 操作者 Account ID */
    private final long actorId;

    /** 本次命令的业务时刻（UTC 微秒） */
    private final Instant now;

    /** 外部命令携带的期望 Root 版本；创建为空 */
    private final Long expectedChangeVersion;

    /** 画像 BCR Root；创建时由 Service 插入后回填 */
    private BusinessChangeRecord root;

    /** 本次保存后的完整条件行（创建 / 修改时为新行，状态推进时为已读取的行） */
    private List<PricingProfileCond> conditions;

    /** DRAFT 修改的目标名称；不修改为空 */
    private String nextProfileName;

    /** DRAFT 修改的目标 Payment Method；不修改时为原值 */
    private String nextPaymentMethodCode;

    /** 状态推进的目标状态；不推进为空 */
    private PricingProfileStatus nextStatus;

    /**
     * 构造一次写入命令的上下文
     *
     * @param profile 画像
     * @param root 画像 BCR Root，创建时为空
     * @param conditions 条件行
     * @param actorId 操作者
     * @param now 业务时刻
     * @param expectedChangeVersion 期望版本，创建时为空
     */
    public PricingProfileContext(PricingProfile profile, BusinessChangeRecord root, List<PricingProfileCond> conditions,
        long actorId, Instant now, Long expectedChangeVersion) {
        this.profile = profile;
        this.root = root;
        this.conditions = conditions;
        this.actorId = actorId;
        this.now = now;
        this.expectedChangeVersion = expectedChangeVersion;
    }

    /**
     * 校验种类、Payment Method 与条件维度相容；用于创建、修改与激活前复核
     * <li>PAYMENT_METHOD 必须有 Payment Method，其他种类必须为空；条件维度必须属于种类允许集合；
     * CURRENCY 为三位、*_COUNTRY 为两位大写字母。</li>
     *
     * @param paymentMethodCode 待保存的 Payment Method
     */
    public void checkDefinition(String paymentMethodCode) {
        PricingProfileKind kind = profile.getProfileKind();
        if (kind == PricingProfileKind.PAYMENT_METHOD && paymentMethodCode == null) {
            throw new BizCheckFailException("PAYMENT_METHOD 画像必须指定 paymentMethodCode: " + profile.getPricingProfileCode());
        }
        if (kind != PricingProfileKind.PAYMENT_METHOD && paymentMethodCode != null) {
            throw new BizCheckFailException("非 PAYMENT_METHOD 画像不能指定 paymentMethodCode: " + profile.getPricingProfileCode()
                + ", 种类: " + kind.getCode());
        }
        Set<PricingDimension> allowed = KIND_DIMENSIONS.get(kind);
        for (PricingProfileCond condition : conditions) {
            if (!allowed.contains(condition.getDimensionCode())) {
                throw new BizCheckFailException("条件维度与画像种类不相容: " + condition.getDimensionCode().getCode() + ", 种类: "
                    + kind.getCode());
            }
            checkValueFormat(condition);
        }
    }

    /** 币种与国家维度的规范化格式。 */
    static void checkValueFormat(PricingProfileCond condition) {
        String value = condition.getConditionValue();
        boolean valid = switch (condition.getDimensionCode()) {
            case CURRENCY -> value.matches("[A-Z]{3}");
            case CONSUMER_COUNTRY, ISSUER_COUNTRY, DESTINATION_COUNTRY -> value.matches("[A-Z]{2}");
            default -> true;
        };
        if (!valid) {
            throw new BizCheckFailException("条件值格式不符合维度要求: " + condition.getDimensionCode().getCode() + "=" + value);
        }
    }
}
