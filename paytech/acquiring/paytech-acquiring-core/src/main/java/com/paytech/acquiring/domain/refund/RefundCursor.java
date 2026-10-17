package com.paytech.commerce.domain.refund;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import com.paytech.commerce.client.api.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.foundation.common.error.FailException;

/** 游标只编码已公开且不可变的排序字段；归属由查询入口核对原退款。 */
public record RefundCursor(Instant createdAt, String refundRequestNo) {
    /** 编码本页最后一条退款的创建时间和公开编号，不携带内部身份或状态。 */
    public String encode() {
        return "r1." + Base64.getUrlEncoder().withoutPadding().encodeToString(
            (createdAt + "\n" + refundRequestNo).getBytes(StandardCharsets.US_ASCII));
    }

    /** 解析既有游标格式；未知版本、非规范编码或不合法排序值统一返回 INVALID_CURSOR。 */
    public static RefundCursor decode(String value) {
        try {
            if (value == null || value.length() > 512 || !value.startsWith("r1.")) {
                throw new IllegalArgumentException();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(value.substring(3)), StandardCharsets.US_ASCII)
                .split("\n", -1);
            if (parts.length != 2 || !parts[1].matches("[A-Za-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException();
            }
            var result = new RefundCursor(Instant.parse(parts[0]), parts[1]);
            if (result.createdAt().getNano() % 1000 != 0 || !result.encode().equals(value)) {
                throw new IllegalArgumentException();
            }
            return result;
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            throw new FailException(CommerceCheckoutErrorCode.INVALID_CURSOR);
        }
    }
}
