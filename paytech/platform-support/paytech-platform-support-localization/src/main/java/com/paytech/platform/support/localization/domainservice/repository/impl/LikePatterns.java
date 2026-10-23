package com.paytech.platform.support.localization.domainservice.repository.impl;

/** LIKE 关键字的字面匹配：转义反斜杠、% 与 _。 */
final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * 转义关键字。
     *
     * @param keyword 关键字；空白视为不筛选
     * @return 转义后的关键字；不筛选时为 null
     */
    static String escape(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
