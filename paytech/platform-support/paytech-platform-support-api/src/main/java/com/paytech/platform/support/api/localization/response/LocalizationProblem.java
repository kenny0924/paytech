package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 一条校验问题；field 为定位路径，message 为可外显的中文原因（ICU 问题带字符位置）。 */
@Getter
@Setter
public class LocalizationProblem {

    /** 定位路径，如 namespaces.team.team.invite.submit 或 translations[3].text。 */
    private String field;

    /** 中文原因。 */
    private String message;
}
