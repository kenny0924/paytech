package com.paytech.commerce.service.processor.checkout.response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.paytech.commerce.domain.checkout.CheckoutPublicOrigin;
import com.paytech.commerce.domain.checkout.CheckoutPublicToken;
import com.paytech.foundation.token.PublicTokenGenerator;

import lombok.Builder;

/** 创建 Checkout 的内部响应；金额以订单币种主单位表示，历史快照 Reader 复用原回执约束。 */
@Builder
public record CreateCheckoutResponse(
    String orderNo,
    String clientOrderId,
    String checkoutUrl,
    String currency,
    BigDecimal merchandiseAmount,
    BigDecimal discountAmount,
    BigDecimal netAmount,
    BigDecimal taxAmount,
    BigDecimal roundingAmount,
    BigDecimal totalAmount,
    Instant expiresAt,
    String location) {

    /** 校验响应金额、时间精度及公开资源坐标。 */
    public CreateCheckoutResponse {
        // 新编号复用公共 Token 主体；该响应还被历史回执 Reader 使用，保留旧订单编号读取。
        boolean validOrderNo = orderNo != null && orderNo.startsWith("ord_")
            && (PublicTokenGenerator.isValid(orderNo.substring(4)) || orderNo.matches("ord_[A-Za-z0-9_-]{22}"));
        if (!validOrderNo
            || clientOrderId == null || clientOrderId.length() > 128 || !clientOrderId.matches("[!-~]+")) {
            throw new IllegalArgumentException("Create Checkout 回执资源坐标无效");
        }
        if (!"USD".equals(currency)) {
            throw new IllegalArgumentException("Create Checkout 回执币种必须为 USD");
        }
        merchandiseAmount = usdAmount(merchandiseAmount, "Merchandise Amount");
        discountAmount = usdAmount(discountAmount, "Discount Amount");
        netAmount = usdAmount(netAmount, "Net Amount");
        if (taxAmount != null) {
            taxAmount = usdAmount(taxAmount, "Tax Amount");
            totalAmount = usdAmount(totalAmount, "Total Amount");
            if (netAmount.add(taxAmount).add(roundingAmount).compareTo(totalAmount) != 0) {
                throw new IllegalArgumentException("创建回执最终金额不一致");
            }
        }
        if (merchandiseAmount.subtract(discountAmount).compareTo(netAmount) != 0) {
            throw new IllegalArgumentException("Create Checkout 回执金额恒等式不成立");
        }
        expiresAt = Objects.requireNonNull(expiresAt, "Expires At 不能为空");
        if (!expiresAt.equals(expiresAt.truncatedTo(ChronoUnit.MICROS))) {
            throw new IllegalArgumentException("Create Checkout 回执 Expires At 必须为微秒精度");
        }
        if (!("/api/v1/orders/" + orderNo).equals(location)) {
            throw new IllegalArgumentException("Create Checkout 回执 Location 与 Order No 不一致");
        }
        validateCheckoutUrl(checkoutUrl);
    }

    /** 验证回执金额并统一为 USD 的两位精度。 */
    private static BigDecimal usdAmount(BigDecimal value, String label) {
        BigDecimal required = Objects.requireNonNull(value, label + " 不能为空");
        if (required.signum() < 0) {
            throw new IllegalArgumentException(label + " 不能为负数");
        }
        return required.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** 回执 URL 必须对应原公开 Origin 和有效 Token，禁止携带额外参数。 */
    private static void validateCheckoutUrl(String checkoutUrl) {
        try {
            URI uri = URI.create(checkoutUrl);
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null
                || uri.getRawPath() == null || !uri.getRawPath().matches("/c/chk_[A-Za-z0-9]{30}")) {
                throw new IllegalArgumentException("Create Checkout 回执 URL 结构无效");
            }
            String token = uri.getRawPath().substring(3);
            String authority = uri.getRawAuthority();
            String origin = new CheckoutPublicOrigin(uri.getScheme() + "://" + authority).value();
            if (!new CheckoutPublicOrigin(origin).checkoutUrl(CheckoutPublicToken.parse(token).value())
                .equals(checkoutUrl)) {
                throw new IllegalArgumentException("Create Checkout 回执 URL 不是 canonical 公开 URL");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Create Checkout 回执 URL 无效", exception);
        }
    }
}
