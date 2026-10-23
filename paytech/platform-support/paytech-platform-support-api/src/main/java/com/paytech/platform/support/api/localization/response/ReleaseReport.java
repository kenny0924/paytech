package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 发布报告。 */
@Getter
@Setter
public class ReleaseReport {

    /** 未翻译而回退英文的 key。 */
    private List<LocaleKey> missing;

    /** 基准已变更仍沿用旧翻译的 key。 */
    private List<LocaleKey> sourceChanged;
}
