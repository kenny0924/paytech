package com.paytech.platform.support.localization.domain.release;

/**
 * 两次发布之间一条文案的变化。
 *
 * @param locale 语言代码
 * @param keyCode 完整 key
 * @param before 旧文案；新增时为 null
 * @param after 新文案；删除时为 null
 */
public record TextChange(String locale, String keyCode, String before, String after) {
}
