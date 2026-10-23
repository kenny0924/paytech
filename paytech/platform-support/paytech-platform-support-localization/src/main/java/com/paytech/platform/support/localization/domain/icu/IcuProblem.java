package com.paytech.platform.support.localization.domain.icu;

/**
 * 一条 ICU 校验问题。
 *
 * @param position 问题所在的字符位置（从 0 开始）；无法定位时为 -1
 * @param reason 中文原因，不含位置前缀
 */
public record IcuProblem(int position, String reason) {

    /**
     * 带位置前缀的完整消息，如“第 5 个字符：缺少右花括号”。
     *
     * @return 可外显的中文消息
     */
    public String message() {
        return position < 0 ? reason : "第 " + (position + 1) + " 个字符：" + reason;
    }
}
