package com.paytech.platform.support.api.localization.response;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** 应用某次发布的语言包清单。 */
@Getter
@Setter
public class ManifestResponseBody {

    /** 应用编码。 */
    private String application;

    /**
     * 发布号
     * <li>应用还没有 READY 发布时为 null，bundles 为空对象。</li>
     */
    private Integer releaseNo;

    /** 发布检查通过时间，UTC ISO-8601；releaseNo 为 null 时为 null。 */
    private String publishedAt;

    /** 可切换的语言：ENABLED 且该发布包含其语言包（en 恒在），按排序值排列。 */
    private List<ManifestLocale> locales;

    /** 应用默认语言。 */
    private String defaultLocale;

    /**
     * 语言代码 → 命名空间编码 → 语言包
     * <li>只含 en 与请求的目标语言。</li>
     */
    private Map<String, Map<String, ManifestBundle>> bundles;
}
