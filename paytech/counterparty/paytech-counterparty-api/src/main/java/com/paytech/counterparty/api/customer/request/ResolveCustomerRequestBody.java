package com.paytech.counterparty.api.customer.request;

import com.paytech.counterparty.api.client.v1.common.ClientApiConstraints;
import com.paytech.counterparty.api.customer.CounterpartyCustomerApi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 建或取 Customer 的请求。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolveCustomerRequestBody {
    /** Client ID。 */
    @NotBlank
    @Pattern(regexp = CounterpartyCustomerApi.DECIMAL_ID)
    private String clientId;

    /** Client 命名空间内的不透明、非 PII 消费者引用。 */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = ClientApiConstraints.CONSUMER_REFERENCE)
    private String consumerReference;

    @Override
    public String toString() {
        return "ResolveCustomerRequestBody[REDACTED]";
    }
}
