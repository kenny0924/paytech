package com.paytech.commercial.api.term.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 价格下的一个计算组件
 * <li>PERCENTAGE：rate 必填、fixedAmount 为空；minAmount / maxAmount 可选，给出时 currency 必填。</li>
 * <li>FIXED：fixedAmount 与 currency 必填，rate、上下限为空。</li>
 * <li>TIERED：头部 rate / fixedAmount 为空，bandBasis、bandMode 与至少一档 bands 必填；其他类型不得有档位。</li>
 */
@Getter
@Setter
public class CommercialTermComponentRequestBody {
    /** 同一价格内唯一的组件代码。 */
    @NotBlank(message = "componentCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "componentCode 格式不正确")
    private String componentCode;

    /** 组件类型：PERCENTAGE、FIXED、TIERED。 */
    @NotBlank(message = "componentType 必填")
    @Pattern(regexp = "PERCENTAGE|FIXED|TIERED", message = "componentType 只能为 PERCENTAGE、FIXED 或 TIERED")
    private String componentType;

    /** 触发计费事件，必须在计费事件目录中登记于本方案条款类型。 */
    @NotBlank(message = "triggerCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "triggerCode 格式不正确")
    private String triggerCode;

    /** 计算基数：GROSS_AMOUNT、NET_AMOUNT、ACTUAL_CLIENT_FEE、FIXED_PER_EVENT、BUY_RATE_DIFFERENCE。 */
    @NotBlank(message = "calculationBase 必填")
    @Pattern(regexp = "GROSS_AMOUNT|NET_AMOUNT|ACTUAL_CLIENT_FEE|FIXED_PER_EVENT|BUY_RATE_DIFFERENCE",
        message = "calculationBase 不在目录内")
    private String calculationBase;

    /** 退款时费用处理：RETURN_ALL、RETURN_PRORATA、KEEP；非支付类事件填 KEEP。 */
    @NotBlank(message = "refundBehavior 必填")
    @Pattern(regexp = "RETURN_ALL|RETURN_PRORATA|KEEP", message = "refundBehavior 不在目录内")
    private String refundBehavior;

    /** 小数费率，0 至 1，最多 10 位小数。 */
    @Pattern(regexp = "(0|1)(\\.[0-9]{1,10})?", message = "rate 必须为 0 至 1 的十进制字符串")
    private String rate;

    /** 固定金额，主单位十进制字符串，最多 8 位小数。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "fixedAmount 格式不正确")
    private String fixedAmount;

    /** 固定金额、上下限或阶梯边界的币种，必须等于价格币种；纯百分比且无上下限时为空。 */
    @Pattern(regexp = "[A-Z]{3}", message = "currency 格式不正确")
    private String currency;

    /** 百分比计算结果的最低金额。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "minAmount 格式不正确")
    private String minAmount;

    /** 百分比计算结果的最高金额。 */
    @Pattern(regexp = "(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "maxAmount 格式不正确")
    private String maxAmount;

    /** 阶梯基数：TRANSACTION_AMOUNT、PERIOD_VOLUME（本期排期时拒绝）；仅 TIERED。 */
    @Pattern(regexp = "TRANSACTION_AMOUNT|PERIOD_VOLUME", message = "bandBasis 不在目录内")
    private String bandBasis;

    /** 阶梯模式：WHOLE_BASE、PROGRESSIVE、RETROACTIVE（本期排期时拒绝）；仅 TIERED。 */
    @Pattern(regexp = "WHOLE_BASE|PROGRESSIVE|RETROACTIVE", message = "bandMode 不在目录内")
    private String bandMode;

    /** 阶梯档位，按列表顺序编号（band_no 从 1 连续）；区间 [lowerBound, upperBound)。 */
    @Size(max = 50, message = "bands 最多 50 档")
    @Valid
    private List<CommercialTermBandRequestBody> bands;
}
