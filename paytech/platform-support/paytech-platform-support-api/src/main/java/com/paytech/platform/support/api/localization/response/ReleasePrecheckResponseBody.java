package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 发布预检：覆盖率、阻止发布的问题与发布报告预览；只读。 */
@Getter
@Setter
public class ReleasePrecheckResponseBody {

    /** 是否可以发布：有 ACTIVE key、必须覆盖语言都达到阈值且没有 ICU 问题。 */
    private boolean publishable;

    /** 预检时的文案修订号。 */
    private int contentRevision;

    /** 下一个发布号。 */
    private int nextReleaseNo;

    /** ACTIVE key 数。 */
    private int activeKeyCount;

    /** 各语言覆盖率。 */
    private List<LocaleCoverage> coverage;

    /** 未翻译而回退英文的 key（ENABLED 非 en 语言），最多 200 条。 */
    private List<LocaleKey> missing;

    /** 未翻译 key 的总数。 */
    private int missingTotal;

    /** 基准已变更仍沿用旧翻译的 key，最多 200 条。 */
    private List<LocaleKey> sourceChanged;

    /** 基准已变更的总数。 */
    private int sourceChangedTotal;

    /**
     * 相对应用当前发布的变更摘要
     * <li>没有当前发布时与空内容比较；语言包读取端口未接入且已有当前发布时为 null。</li>
     */
    private ReleaseChangeSummary changeSummary;

    /** 阻止发布的 ICU 问题（翻译与当前基准不一致等），最多 200 条。 */
    private List<LocalizationProblem> problems;
}
