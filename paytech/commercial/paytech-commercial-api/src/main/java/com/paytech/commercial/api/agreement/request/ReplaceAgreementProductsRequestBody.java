package com.paytech.commercial.api.agreement.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 整体替换合同订阅的可选产品集合；集合等价时不产生变更。 */
@Getter
@Setter
public class ReplaceAgreementProductsRequestBody {
    /**
     * 调用方读取到的合同 BCR Root 版本（changeVersion）
     * <li>本次写入以它做 CAS，提交后版本加一；不一致返回 409。</li>
     */
    @NotNull(message = "expectedChangeVersion 必填")
    @Positive(message = "expectedChangeVersion 必须为正")
    private Long expectedChangeVersion;

    /**
     * 订阅的产品代码，可为空集合，不得重复，最多 20 个
     * <li>产品须 ACTIVE、非基座且 businessModeScope 包含合同业务模式；仍有开通项的产品不能移除。</li>
     */
    @NotNull(message = "productCodes 必填")
    @Size(max = 20, message = "productCodes 最多 20 个")
    private List<@NotNull(message = "productCodes 不能含空值") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "productCodes 必须是大写产品代码") String> productCodes;
}
