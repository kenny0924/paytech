package com.paytech.acquiring.service.processor.customer.request;

import com.paytech.acquiring.domain.customer.CheckoutSubmissionModels.CardInput;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.NetworkEvidence;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 当前支付的内部输入，不持有 HTTP Body、Servlet 或数据库事务。 */
@Getter
@AllArgsConstructor
public class SubmitCustomerPaymentRequest {
    /** 原公开 Checkout Token。 */
    private final String checkoutToken;
    /** 当前已展示合同的主键。 */
    @Positive
    private final long consumerContractId;
    /** 必须明确同意合同，不从服务器默认值推导。 */
    @NotNull
    @AssertTrue
    private final Boolean confirmed;
    /** 当前请求瞬时持有的手输卡输入；使用已存卡时为空。 */
    @Valid
    private final CardInput card;
    /** 使用已存卡时的 Token；手输卡时为空。 */
    private final String storedCardToken;
    /** 使用已存卡时本次输入的 CVV；手输卡时为空。 */
    private final String storedCardCvv;
    /** 是否同意支付成功后保存手输卡。 */
    private final boolean saveCard;
    /** 入站适配器从可信连接取得的网络证据。 */
    @NotNull
    private final NetworkEvidence evidence;
    /** 公共组件提供的请求标识。 */
    @NotBlank
    @Size(max = 128)
    private final String requestId;
}
