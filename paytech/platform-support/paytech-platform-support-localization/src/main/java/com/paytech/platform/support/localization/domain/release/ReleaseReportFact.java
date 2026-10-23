package com.paytech.platform.support.localization.domain.release;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发布报告；同时是 t_i18n_release.report 的 JSON 结构。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseReportFact {

    /** 未翻译而回退英文的 key。 */
    private List<LocaleKeyRef> missing;

    /** 基准已变更仍沿用旧翻译的 key。 */
    private List<LocaleKeyRef> sourceChanged;

    /**
     * 相对发布时线上发布的变更摘要
     * <li>语言包读取端口未接入且发布时已有线上发布时为 null；早于该字段的发布也为 null。</li>
     */
    private ReleaseChangeSummaryFact changeSummary;
}
