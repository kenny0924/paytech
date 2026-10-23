package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 发布详情：摘要、覆盖率、报告与语言包。 */
@Getter
@Setter
public class ReleaseDetailResponseBody {

    /** 摘要。 */
    private ReleaseSummaryResponseBody release;

    /** 发布所依据的文案修订号。 */
    private int basedOnContentRevision;

    /** 发布时各语言覆盖率。 */
    private List<LocaleCoverage> coverage;

    /** 发布报告。 */
    private ReleaseReport report;

    /** 语言包文件检查进度，按文件服务当前状态计算。 */
    private BundleCheckProgress checkProgress;

    /** 语言包，按语言、命名空间排列。 */
    private List<ReleaseBundleSummary> bundles;
}
