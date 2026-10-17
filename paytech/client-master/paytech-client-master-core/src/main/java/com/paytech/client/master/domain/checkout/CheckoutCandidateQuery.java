package com.paytech.client.master.domain.checkout;

import static com.paytech.client.master.api.enums.CheckoutCandidateErrorCode.INVALID_CANDIDATE_QUERY;

import java.time.DateTimeException;
import java.time.Instant;

import com.paytech.foundation.common.error.FailException;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * 通过边界验证的候选查询条件；格式规则与 commercial 的 Checkout 查询一致。
 * <li>不比较本机时钟：商业时点是否晚于当前时间由 commercial 判断，避免两服务时钟差造成误拒。</li>
 */
@Value
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CheckoutCandidateQuery {
    /** 已授权的 Client 身份。 */
    long clientId;

    /** 消费者国家。 */
    String country;

    /** 交易币种。 */
    String currency;

    /** 法律文档语言。 */
    String locale;

    /** Commerce 冻结的商业时点。 */
    Instant asOf;

    /**
     * 解析并校验候选查询条件。
     *
     * @param clientId 规范正十进制 Client ID
     * @param country 两位大写国家代码
     * @param currency 三位大写币种
     * @param locale 法律 Locale
     * @param asOf UTC RFC 3339 商业时点，最多六位小数
     * @return 规范查询；任一字段不合法时抛出 INVALID_CANDIDATE_QUERY
     */
    public static CheckoutCandidateQuery resolve(String clientId, String country, String currency, String locale,
        String asOf) {
        if (clientId == null || !clientId.matches("[1-9][0-9]{0,18}") || country == null || !country.matches("[A-Z]{2}")
            || currency == null || !currency.matches("[A-Z]{3}") || locale == null || locale.length() > 16
            || !locale.matches("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*") || asOf == null
            || !asOf.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z")) {
            throw new FailException(INVALID_CANDIDATE_QUERY);
        }
        try {
            return new CheckoutCandidateQuery(Long.parseLong(clientId), country, currency, locale, Instant.parse(asOf));
        } catch (NumberFormatException | DateTimeException exception) {
            throw new FailException(INVALID_CANDIDATE_QUERY);
        }
    }
}
