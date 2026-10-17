package com.paytech.client.master.domain.onboarding;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 证件号通用规范化（2026-10-13 确认，不按国家区分）。
 * <li>NFKC → 去首尾空白 → 删除空格、-、.、/ → 转大写；结果须为 1—128 位大写字母或数字。</li>
 */
public final class ClientIdentifierNormalizer {
    /** 全局标识（如 LEI）的签发作用域。 */
    public static final String GLOBAL_SCOPE = "GLOBAL";

    private ClientIdentifierNormalizer() {
    }

    /**
     * 规范化证件号
     *
     * @param raw 客户填报的原始号码
     * @return 规范化结果；为空或不符合规则时返回 null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip()
            .replaceAll("[\\s\\-./]", "").toUpperCase(Locale.ROOT);
        return value.matches("[A-Z0-9]{1,128}") ? value : null;
    }

    /** 签发作用域：要求签发国时为国家代码，否则为 GLOBAL。 */
    public static String issuerScopeKey(boolean issuingCountryRequired, String issuingCountry) {
        return issuingCountryRequired ? issuingCountry : GLOBAL_SCOPE;
    }
}
