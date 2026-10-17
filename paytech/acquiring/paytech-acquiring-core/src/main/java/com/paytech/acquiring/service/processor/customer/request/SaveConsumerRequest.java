package com.paytech.acquiring.service.processor.customer.request;

import java.net.IDN;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.Address;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ConsumerDetails;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 当前保存请求的内部输入；个人原值和 Checkout Token 不进入通用日志或快照。 */
@Getter
@AllArgsConstructor
public class SaveConsumerRequest {
    /** 原公开 Checkout Token。 */
    private final String checkoutToken;

    /** 本次完整替换的消费者资料。 */
    private final ConsumerDetails details;

    /** 只做当前输入的纯规范化，保留 local-part，不改订单国家。 */
    public ConsumerDetails normalizeDetails() {
        if (details == null || details.billingAddress() == null) {
            throw invalid();
        }
        var address = details.billingAddress();
        String country = text(address.countryCode(), 2, true);
        if (!country.matches("[A-Z]{2}") || !Set.of(Locale.getISOCountries()).contains(country)) {
            throw invalid();
        }
        return new ConsumerDetails(text(details.fullName(), 200, true), normalizeEmail(details.email()),
            new Address(country, text(address.addressLine1(), 255, true), text(address.addressLine2(), 255, false),
                text(address.region(), 64, false), text(address.city(), 128, true),
                text(address.postalCode(), 32, false)));
    }

    /** 域名转 ASCII 并小写；姓名和地址之外的邮箱 local-part 不做大小写折叠。 */
    static String normalizeEmail(String value) {
        String email = text(value, 254, true);
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1 || email.indexOf('@') != at
            || email.chars().anyMatch(Character::isWhitespace)) {
            throw invalid();
        }
        try {
            email = email.substring(0, at) + "@"
                + IDN.toASCII(email.substring(at + 1), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException invalidDomain) {
            throw invalid();
        }
        if (email.length() > 254 || !email.substring(at + 1).contains(".")) {
            throw invalid();
        }
        return email;
    }

    /** 拒绝控制字符；可选空值保留为 null，非空输入去首尾空白并规范为 NFC。 */
    static String text(String value, int max, boolean required) {
        if (value == null) {
            if (required) {
                throw invalid();
            }
            return null;
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        String result = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
        if (result.isEmpty() || result.length() > max) {
            throw invalid();
        }
        return result;
    }

    /** 保留本接口的字段约束错误合同。 */
    static FailException invalid() {
        return new FailException(CommonErrorCode.VALIDATION_ERROR);
    }
}
