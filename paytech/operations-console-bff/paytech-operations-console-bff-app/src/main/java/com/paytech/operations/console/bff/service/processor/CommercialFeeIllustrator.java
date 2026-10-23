package com.paytech.operations.console.bff.service.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.ComponentIllustration;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.FeeEventIllustration;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.FeeIllustration;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.IllustrationNote;
import com.paytech.operations.console.bff.service.model.CommercialPricePreviewModels.IllustrationNoteCode;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPrice;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPriceBand;
import com.paytech.operations.console.bff.service.model.CommercialPricingModels.CommercialPriceComponent;

/**
 * 价格试算的示例费用：只按命中价格与单笔示例金额做无状态纯计算，不读库、不调用外部依赖，结果不是计费事实
 * <li>百分比 = 基数 × 费率 + 固定额；固定 = 固定额；阶梯只支持 TRANSACTION_AMOUNT：WHOLE_BASE 取 [下界, 上界) 命中档，
 * 用档位费率乘完整基数或取档位固定额；PROGRESSIVE 各档区间分别乘档位费率后相加。</li>
 * <li>约束前金额保留 8 位小数 HALF_UP；之后按组件最低 / 最高金额约束，再按币种精度 HALF_UP 归整（与 billing 渠道成本计算口径一致）。</li>
 * <li>ACTUAL_CLIENT_FEE、BUY_RATE_DIFFERENCE 依赖其他费用结果，PERIOD_VOLUME / RETROACTIVE 依赖周期累计量，均标为未计算。</li>
 * <li>commercial 价格中的金额或费率不是十进制字符串时，按上游无效响应处理。</li>
 * <li>说明只返回编码与参数（{@link IllustrationNoteCode}），文案由前端翻译。</li>
 *
 * @author Claude
 */
final class CommercialFeeIllustrator {
    /** 约束前金额的计算精度。 */
    static final int RAW_SCALE = 8;

    /** 无法识别的币种使用的默认精度。 */
    static final int DEFAULT_FRACTION_DIGITS = 2;

    private static final String PERCENTAGE = "PERCENTAGE";
    private static final String FIXED = "FIXED";
    private static final String TIERED = "TIERED";
    private static final String WHOLE_BASE = "WHOLE_BASE";
    private static final String PROGRESSIVE = "PROGRESSIVE";

    private CommercialFeeIllustrator() {
    }

    /**
     * 按命中价格计算示例费用
     *
     * @param price 命中价格
     * @param amount 示例金额（已按格式校验）
     * @return 按计费事件分组的示例费用
     */
    static FeeIllustration illustrate(CommercialPrice price, String amount) {
        BigDecimal basis = new BigDecimal(amount);
        int fractionDigits = fractionDigits(price.getCurrency());
        Map<String, List<ComponentIllustration>> byEvent = new TreeMap<>();
        price.getComponents().stream()
            .sorted(Comparator.comparing(CommercialPriceComponent::getComponentNo, Comparator.nullsLast(Integer::compareTo)))
            .forEach(component -> byEvent.computeIfAbsent(Objects.requireNonNullElse(component.getTriggerCode(), ""),
                key -> new ArrayList<>()).add(component(component, basis, fractionDigits)));
        var illustration = new FeeIllustration();
        illustration.setAmount(amount);
        illustration.setCurrency(price.getCurrency());
        illustration.setFractionDigits(fractionDigits);
        illustration.setFeeEvents(byEvent.entrySet().stream().map(entry -> feeEvent(entry.getKey(), entry.getValue()))
            .toList());
        return illustration;
    }

    /** 币种精度；无法识别时使用 2 位。 */
    static int fractionDigits(String currency) {
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return digits < 0 ? DEFAULT_FRACTION_DIGITS : digits;
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return DEFAULT_FRACTION_DIGITS;
        }
    }

    private static FeeEventIllustration feeEvent(String feeEventCode, List<ComponentIllustration> components) {
        var event = new FeeEventIllustration();
        event.setFeeEventCode(feeEventCode);
        event.setComponents(components);
        event.setTotalAmount(components.stream().filter(ComponentIllustration::isComputed)
            .map(component -> new BigDecimal(component.getAmount())).reduce(BigDecimal::add)
            .map(BigDecimal::toPlainString).orElse(null));
        return event;
    }

    /** 一个组件：先判断能否由单笔金额计算，再计算、约束与归整。 */
    static ComponentIllustration component(CommercialPriceComponent component, BigDecimal basis, int fractionDigits) {
        var result = new ComponentIllustration();
        result.setComponentNo(component.getComponentNo());
        result.setComponentCode(component.getComponentCode());
        result.setComponentType(component.getComponentType());
        result.setCalculationBase(component.getCalculationBase());
        IllustrationNote unsupported = unsupportedReason(component);
        if (unsupported != null) {
            result.setNotes(List.of(unsupported));
            return result;
        }
        List<IllustrationNote> notes = new ArrayList<>();
        BigDecimal raw = raw(component, basis, notes).setScale(RAW_SCALE, RoundingMode.HALF_UP);
        BigDecimal bounded = clamp(component, raw, notes);
        result.setComputed(true);
        result.setRawAmount(raw.toPlainString());
        result.setAmount(bounded.setScale(fractionDigits, RoundingMode.HALF_UP).toPlainString());
        result.setNotes(List.copyOf(notes));
        return result;
    }

    /** 不能由单笔示例金额得到的组件的未计算原因；可计算时为 null。 */
    static IllustrationNote unsupportedReason(CommercialPriceComponent component) {
        String base = component.getCalculationBase();
        if (CalculationBase.ACTUAL_CLIENT_FEE.getCode().equals(base)
            || CalculationBase.BUY_RATE_DIFFERENCE.getCode().equals(base)) {
            return note(IllustrationNoteCode.BASE_DEPENDS_ON_FEE, "base", base);
        }
        String type = component.getComponentType();
        if (!PERCENTAGE.equals(type) && !FIXED.equals(type) && !TIERED.equals(type)) {
            return note(IllustrationNoteCode.COMPONENT_UNSUPPORTED, "type", type);
        }
        if (TIERED.equals(type) && !BandBasis.TRANSACTION_AMOUNT.getCode().equals(component.getBandBasis())) {
            return note(IllustrationNoteCode.BAND_BASIS_PERIODIC, "basis", component.getBandBasis());
        }
        if (TIERED.equals(type) && !WHOLE_BASE.equals(component.getBandMode())
            && !PROGRESSIVE.equals(component.getBandMode())) {
            return note(IllustrationNoteCode.BAND_MODE_UNSUPPORTED, "mode", component.getBandMode());
        }
        return null;
    }

    /** 一条说明；参数值为 null 时以空串返回，保证前端插值不出现 "null"。 */
    static IllustrationNote note(IllustrationNoteCode code, String name, Object value) {
        Map<String, String> params = new LinkedHashMap<>();
        if (name != null) {
            params.put(name, value == null ? "" : String.valueOf(value));
        }
        return new IllustrationNote(code, Map.copyOf(params));
    }

    private static BigDecimal raw(CommercialPriceComponent component, BigDecimal basis, List<IllustrationNote> notes) {
        return switch (component.getComponentType()) {
            case PERCENTAGE -> basis.multiply(decimal(component.getRate())).add(decimal(component.getFixedAmount()));
            case FIXED -> decimal(component.getFixedAmount());
            default -> WHOLE_BASE.equals(component.getBandMode()) ? wholeBase(component, basis, notes)
                : progressive(component, basis, notes);
        };
    }

    /** WHOLE_BASE：命中档 [下界, 上界) 的费率乘完整基数，或取档位固定额。 */
    static BigDecimal wholeBase(CommercialPriceComponent component, BigDecimal basis, List<IllustrationNote> notes) {
        for (CommercialPriceBand band : bands(component)) {
            if (contains(band, basis)) {
                notes.add(note(IllustrationNoteCode.BAND_HIT, "bandNo", band.getBandNo()));
                return band.getRate() != null ? basis.multiply(decimal(band.getRate())) : decimal(band.getFixedAmount());
            }
        }
        notes.add(note(IllustrationNoteCode.BAND_NONE, null, null));
        return BigDecimal.ZERO;
    }

    /** PROGRESSIVE：每档取基数落在 [下界, 上界) 内的部分乘档位费率后相加。 */
    static BigDecimal progressive(CommercialPriceComponent component, BigDecimal basis, List<IllustrationNote> notes) {
        BigDecimal total = BigDecimal.ZERO;
        int used = 0;
        for (CommercialPriceBand band : bands(component)) {
            BigDecimal lower = decimal(band.getLowerBound());
            if (basis.compareTo(lower) <= 0) {
                continue;
            }
            BigDecimal upper = band.getUpperBound() == null ? basis : decimal(band.getUpperBound()).min(basis);
            total = total.add(upper.subtract(lower).multiply(decimal(band.getRate())));
            used++;
        }
        notes.add(note(IllustrationNoteCode.PROGRESSIVE_BANDS, "count", used));
        return total;
    }

    /** 最低 / 最高金额约束。 */
    static BigDecimal clamp(CommercialPriceComponent component, BigDecimal raw, List<IllustrationNote> notes) {
        if (component.getMinAmount() != null && raw.compareTo(decimal(component.getMinAmount())) < 0) {
            notes.add(note(IllustrationNoteCode.MIN_APPLIED, "amount", component.getMinAmount()));
            return decimal(component.getMinAmount());
        }
        if (component.getMaxAmount() != null && raw.compareTo(decimal(component.getMaxAmount())) > 0) {
            notes.add(note(IllustrationNoteCode.MAX_APPLIED, "amount", component.getMaxAmount()));
            return decimal(component.getMaxAmount());
        }
        return raw;
    }

    private static List<CommercialPriceBand> bands(CommercialPriceComponent component) {
        if (component.getBands() == null || component.getBands().isEmpty()) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return component.getBands().stream()
            .sorted(Comparator.comparing(CommercialPriceBand::getBandNo, Comparator.nullsLast(Integer::compareTo))).toList();
    }

    private static boolean contains(CommercialPriceBand band, BigDecimal basis) {
        return basis.compareTo(decimal(band.getLowerBound())) >= 0
            && (band.getUpperBound() == null || basis.compareTo(decimal(band.getUpperBound())) < 0);
    }

    /** 空值视为 0；非十进制字符串按上游无效响应处理。 */
    private static BigDecimal decimal(String value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException invalid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, invalid);
        }
    }
}
