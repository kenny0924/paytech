package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 一次上线检查的结论（技术方案 3.3 第 3 步）；补偿任务据此写发布状态或重试，不持久化。 */
@Getter
@RequiredArgsConstructor
public enum ReleaseCheckVerdict implements CodeEnum {

    /** 全部语言包可用：标为 READY 并按发布号决定是否上线。 */
    READY("READY"),

    /** 有语言包被拒绝：标为 FAILED 并列出被拒绝的语言包。 */
    REJECTED("REJECTED"),

    /** 已是允许的最后一次尝试仍未检查完：标为 FAILED，原因“文件检查超时”。 */
    TIMED_OUT("TIMED_OUT"),

    /** 仍在检查：本次可重试。 */
    RETRY("RETRY");

    private final String code;
}
