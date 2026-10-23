package com.paytech.platform.support.localization.domain.release;

/**
 * 某语言下的一个 key。
 *
 * @param locale 语言代码
 * @param keyCode 完整 key
 */
public record LocaleKeyRef(String locale, String keyCode) {
}
