package com.paytech.compliance.core.enums;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/**
 * Client 入驻审核命令的业务失败
 * <li>与 risk-api 发布的同名 Code 一致；业务模块不依赖 API 模块，故在 Owner 内部声明。</li>
 */
public enum ClientReviewErrorCode implements CodeMessageEnum {
    CLIENT_REVIEW_NOT_FOUND("审核任务或材料不存在"),

    CLIENT_REVIEW_CLOSED("审核任务已结束，不能再修改"),

    CLIENT_REVIEW_CHANGED("审核任务已被其他操作改变，请刷新后确认"),

    CLIENT_REVIEW_NOT_READY("审核尚未满足通过条件"),

    CLIENT_REVIEW_EVIDENCE_REVIEWED("材料已核验为其他结果"),

    CLIENT_REVIEW_CLASSIFICATION_CONFLICT("分类与该任务已接收的分类不一致"),

    CLIENT_REVIEW_CHECK_RECORDED("检查已录入其他结论"),

    CLIENT_REVIEW_HIT_CLOSED("筛查命中已关闭或已被新的筛查取代");

    private final String message;

    /** 保存可外显的安全业务说明。 */
    ClientReviewErrorCode(String message) { this.message = message; }
    /** 返回稳定 Wire 错误代码。 */
    @Override public String getCode() { return name(); }
    /** 返回默认可外显的业务说明。 */
    @Override public String getMessage() { return message; }
}
