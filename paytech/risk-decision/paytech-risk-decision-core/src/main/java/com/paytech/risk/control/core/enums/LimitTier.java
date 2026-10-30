package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Client 限额档位（REQ-04）
 * <li>放量顺序 VERIFICATION &lt; OBSERVATION &lt; STANDARD；RESTRICTED 不在顺序中，不满足任何"不低于"判断。</li>
 * <li>每个档位对应一张系统维护的 TIER 名单，名单编码为 TIER_ 加档位代码。</li>
 */
public enum LimitTier implements CodeEnum {
    /** 验证档：承保批准时 Client 尚无档位则自动进入。 */
    VERIFICATION("VERIFICATION", 1),

    /** 观察档：上线复核批准后进入。 */
    OBSERVATION("OBSERVATION", 2),

    /** 标准档：观察期满确认后进入。 */
    STANDARD("STANDARD", 3),

    /** 受限档：新交易按流程配置拒绝。 */
    RESTRICTED("RESTRICTED", 0);

    private final String code;

    private final int rank;

    LimitTier(String code, int rank) {
        this.code = code;
        this.rank = rank;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** 本档位对应的系统 TIER 名单编码。 */
    public String listCode() {
        return "TIER_" + code;
    }

    /** 放量顺序中的位置；RESTRICTED 为 0。 */
    public int rank() {
        return rank;
    }

    /**
     * 是否允许从本档位申请变更到目标档位（B3）
     * <li>VERIFICATION → OBSERVATION（上线复核）；OBSERVATION → STANDARD 或 OBSERVATION（延长观察）；</li>
     * <li>非 RESTRICTED → RESTRICTED；RESTRICTED → 不高于受限前档位的 VERIFICATION / OBSERVATION / STANDARD（无记录按 VERIFICATION）。</li>
     *
     * @param target 目标档位
     * @param previousTier 进入 RESTRICTED 前的档位；非 RESTRICTED 不使用
     * @return 允许申请
     */
    public boolean allowsRequestTo(LimitTier target, LimitTier previousTier) {
        if (this == RESTRICTED) {
            LimitTier ceiling = previousTier == null ? VERIFICATION : previousTier;
            return target != RESTRICTED && target.rank <= ceiling.rank;
        }
        if (target == RESTRICTED) {
            return true;
        }
        return switch (this) {
            case VERIFICATION -> target == OBSERVATION;
            case OBSERVATION -> target == STANDARD || target == OBSERVATION;
            default -> false;
        };
    }

    /** 本档位未配置额度的币种是否放行：只有 STANDARD 放行（不设额度）。 */
    public boolean allowsUnconfiguredCurrency() {
        return this == STANDARD;
    }
}
