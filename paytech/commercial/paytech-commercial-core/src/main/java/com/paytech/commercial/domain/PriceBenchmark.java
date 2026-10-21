package com.paytech.commercial.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.paytech.commercial.api.enums.PriceBenchmarkStatus;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponent;
import com.paytech.commercial.core.dal.dataobject.CommercialTermComponentBand;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 合同价格组件与来源模板同组件的比较结果（破标，technique.md §4.5）
 * <li>费率 / 固定额 / 最低收费 / 最高收费：合同值低于模板值即 BELOW_STANDARD；模板有最低收费而合同为空也算，最高收费为空不算。</li>
 * <li>阶梯：档位结构（档数与上下界）相同时任一档费率或固定额低于模板同档为 BELOW_STANDARD；结构不同为 NO_BENCHMARK。</li>
 * <li>模板组件不存在（按组件代码匹配）或组件类型不同为 NO_BENCHMARK；纯计算，不访问数据库。</li>
 */
@Getter
@AllArgsConstructor
public class PriceBenchmark {
    /** 无来源模板组件。 */
    public static final PriceBenchmark NONE = new PriceBenchmark(PriceBenchmarkStatus.NO_BENCHMARK, null, null, null, null);

    /** 比较结果 */
    private final PriceBenchmarkStatus status;

    /** 模板费率 */
    private final BigDecimal standardRate;

    /** 模板固定金额 */
    private final BigDecimal standardFixedAmount;

    /** 模板最低收费 */
    private final BigDecimal standardMinAmount;

    /** 模板最高收费 */
    private final BigDecimal standardMaxAmount;

    /**
     * 比较合同组件与模板组件
     *
     * @param component 合同组件
     * @param bands 合同组件档位
     * @param standard 来源模板同代码组件，可空
     * @param standardBands 模板组件档位
     * @return 比较结果
     */
    public static PriceBenchmark compare(CommercialTermComponent component, List<CommercialTermComponentBand> bands,
        CommercialTermComponent standard, List<CommercialTermComponentBand> standardBands) {
        if (standard == null || standard.getComponentType() != component.getComponentType()) {
            return NONE;
        }
        PriceBenchmarkStatus status;
        if (!bands.isEmpty() || !standardBands.isEmpty()) {
            status = compareBands(sorted(bands), sorted(standardBands));
        } else {
            boolean below = lower(component.getRate(), standard.getRate())
                || lower(component.getFixedAmount(), standard.getFixedAmount())
                || standard.getMinAmount() != null && (component.getMinAmount() == null
                    || component.getMinAmount().compareTo(standard.getMinAmount()) < 0)
                || lower(component.getMaxAmount(), standard.getMaxAmount());
            status = below ? PriceBenchmarkStatus.BELOW_STANDARD : PriceBenchmarkStatus.AT_OR_ABOVE;
        }
        return new PriceBenchmark(status, standard.getRate(), standard.getFixedAmount(), standard.getMinAmount(),
            standard.getMaxAmount());
    }

    /** 档位结构相同时逐档比较费率与固定额。 */
    static PriceBenchmarkStatus compareBands(List<CommercialTermComponentBand> bands, List<CommercialTermComponentBand> standard) {
        if (bands.size() != standard.size()) {
            return PriceBenchmarkStatus.NO_BENCHMARK;
        }
        boolean below = false;
        for (int index = 0; index < bands.size(); index++) {
            CommercialTermComponentBand band = bands.get(index);
            CommercialTermComponentBand base = standard.get(index);
            if (!sameDecimal(band.getLowerBound(), base.getLowerBound()) || !sameDecimal(band.getUpperBound(), base.getUpperBound())) {
                return PriceBenchmarkStatus.NO_BENCHMARK;
            }
            below = below || lower(band.getRate(), base.getRate()) || lower(band.getFixedAmount(), base.getFixedAmount());
        }
        return below ? PriceBenchmarkStatus.BELOW_STANDARD : PriceBenchmarkStatus.AT_OR_ABOVE;
    }

    /** 两者都有值且合同值低于模板值。 */
    static boolean lower(BigDecimal value, BigDecimal standard) {
        return value != null && standard != null && value.compareTo(standard) < 0;
    }

    /** 十进制按数值比较，空与空相等。 */
    static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    /** 按档位序号排序。 */
    static List<CommercialTermComponentBand> sorted(List<CommercialTermComponentBand> bands) {
        return bands.stream().filter(Objects::nonNull).sorted(Comparator.comparing(CommercialTermComponentBand::getBandNo)).toList();
    }
}
