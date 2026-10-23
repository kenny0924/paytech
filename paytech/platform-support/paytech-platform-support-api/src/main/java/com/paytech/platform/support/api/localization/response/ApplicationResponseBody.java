package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一个语言包消费方应用的设置与当前发布号。 */
@Getter
@Setter
public class ApplicationResponseBody {

    /** 应用编码，如 client-console。 */
    private String applicationCode;

    /** 应用名称。 */
    private String applicationName;

    /** 默认语言代码。 */
    private String defaultLocale;

    /** 必须覆盖的语言代码，可为空数组。 */
    private List<String> requiredLocales;

    /** 覆盖率阈值百分比，0 至 100。 */
    private int coverageThresholdPercent;

    /** 状态：ENABLED、DISABLED。 */
    private String status;

    /**
     * 当前发布号
     * <li>还没有上线的发布时为 null，前端只用构建内置文案。</li>
     */
    private Integer currentReleaseNo;

    /** key 集合修订号，每次确认导入加一。 */
    private int keySetRevision;

    /** 文案修订号，每次导入 key、保存或导入翻译加一。 */
    private int contentRevision;

    /**
     * 未发布变更数
     * <li>当前发布登记之后，key 行或其任一翻译行被写入过的不同 key 数（含被废弃的 key）；没有当前发布时为 ACTIVE key 数。</li>
     */
    private long pendingChangeCount;

    /** 设置的乐观并发版本，修改设置时按此传 expectedVersion；文案变化不改变它。 */
    private int version;

    /** 最后修改设置的账号 ID，十进制字符串。 */
    private String updatedBy;

    /** 最后修改时间，UTC ISO-8601 微秒精度。 */
    private String updateTime;
}
