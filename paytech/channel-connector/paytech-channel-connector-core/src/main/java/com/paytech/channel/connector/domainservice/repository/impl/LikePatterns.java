package com.paytech.channel.connector.domainservice.repository.impl;

/** MySQL LIKE 前缀查询的字面量转义；关键字中的通配符按文字处理。 */
final class LikePatterns {

    /** 工具类不实例化。 */
    private LikePatterns() {
    }

    /**
     * 转义反斜杠、百分号与下划线
     *
     * @param keyword 用户输入的关键字，可空
     * @return 可直接拼接 % 的字面量；空或全空白时为 null，表示不按关键字筛选
     */
    static String escape(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
