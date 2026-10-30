package com.paytech.operations.console.bff.domain.enums;

import java.util.Arrays;
import java.util.List;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Client 限额档位（risk-decision REQ-04 的稳定 Code）；BFF 只用于计算页面可申请的目标档位
 * <li>高低：VERIFICATION &lt; OBSERVATION &lt; STANDARD；RESTRICTED 不参与比较。</li>
 */
public enum LimitTier implements CodeEnum {
    /** 验证档。 */
    VERIFICATION("VERIFICATION", 1),

    /** 观察档。 */
    OBSERVATION("OBSERVATION", 2),

    /** 标准档。 */
    STANDARD("STANDARD", 3),

    /** 受限。 */
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

    /**
     * 按 Code 解析；未知或空为 null（页面容错未知值）
     *
     * @param code 档位 Code
     * @return 档位或 null
     */
    public static LimitTier of(String code) {
        return Arrays.stream(values()).filter(tier -> tier.code.equals(code)).findFirst().orElse(null);
    }

    /**
     * 允许申请的目标档位（B3）
     * <li>VERIFICATION → OBSERVATION、RESTRICTED；OBSERVATION → STANDARD、OBSERVATION（延长）、RESTRICTED；STANDARD → RESTRICTED。</li>
     * <li>RESTRICTED → 不高于受限前档位的档位；受限前档位未知时只能回 VERIFICATION。</li>
     *
     * @param previousTier 受限前档位，仅 RESTRICTED 使用
     * @return 目标档位 Code，按档位从低到高
     */
    public List<String> requestableTargets(LimitTier previousTier) {
        return switch (this) {
            case VERIFICATION -> List.of(OBSERVATION.code, RESTRICTED.code);
            case OBSERVATION -> List.of(STANDARD.code, OBSERVATION.code, RESTRICTED.code);
            case STANDARD -> List.of(RESTRICTED.code);
            case RESTRICTED -> {
                int ceiling = previousTier == null || previousTier == RESTRICTED ? VERIFICATION.rank : previousTier.rank;
                yield Arrays.stream(values()).filter(tier -> tier.rank > 0 && tier.rank <= ceiling).map(LimitTier::getCode).toList();
            }
        };
    }
}
