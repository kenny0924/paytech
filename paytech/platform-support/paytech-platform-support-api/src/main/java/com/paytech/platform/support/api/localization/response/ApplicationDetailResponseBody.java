package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 应用详情：设置、当前发布、key 数与覆盖率摘要。 */
@Getter
@Setter
public class ApplicationDetailResponseBody {

    /** 应用设置。 */
    private ApplicationResponseBody application;

    /**
     * 当前发布
     * <li>没有时为 null。</li>
     */
    private ReleaseSummaryResponseBody currentRelease;

    /** ACTIVE key 数。 */
    private long activeKeyCount;

    /** DEPRECATED key 数。 */
    private long deprecatedKeyCount;

    /** 命名空间数。 */
    private int namespaceCount;

    /** en、必须覆盖语言与其他 ENABLED 语言的覆盖率，按语言排序值排列。 */
    private List<LocaleCoverage> coverage;
}
