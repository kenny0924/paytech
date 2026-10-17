package com.paytech.acquiring.api.checkout.v1.checkout.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 完整替换消费者资料；未提供的可选地址成员清空，不修改订单计税国家。 */
@Getter
@Setter
public class SaveConsumerRequestBody {
    /** 消费者姓名；服务端去首尾空白并规范为 NFC。 */
    @NotBlank
    @Size(max = 200)
    private String fullName;

    /** 消费者邮箱；规范域名后校验，保留 local-part 大小写和加号。 */
    @NotBlank
    @Size(max = 254)
    private String email;

    /** 完整账单地址。 */
    @Valid
    @NotNull
    private BillingAddressInfo billingAddress;

    /** 账单地址，不覆盖创建时固定的消费者计税国家。 */
    @Getter
    @Setter
    public static class BillingAddressInfo {
        /** 两位大写国家代码。 */
        @NotBlank
        @Pattern(regexp = "[A-Z]{2}")
        private String countryCode;

        /** 地址第一行。 */
        @NotBlank
        @Size(max = 255)
        private String addressLine1;

        /** 可选地址第二行；省略或 null 表示清空。 */
        @Size(min = 1, max = 255)
        private String addressLine2;

        /** 可选省、州或地区。 */
        @Size(min = 1, max = 64)
        private String region;

        /** 城市。 */
        @NotBlank
        @Size(max = 128)
        private String city;

        /** 可选邮政编码。 */
        @Size(min = 1, max = 32)
        private String postalCode;
    }
}
