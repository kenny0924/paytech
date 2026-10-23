package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 翻译工作台中的一条：key、英文基准与目标语言翻译。 */
@Getter
@Setter
public class TranslationEntryResponseBody {

    /** 命名空间编码。 */
    private String namespaceCode;

    /** 完整 key。 */
    private String keyCode;

    /** 英文基准。 */
    private String sourceText;

    /** 开发者说明，可为 null。 */
    private String description;

    /** 英文基准中的参数名，按首次出现排列；plural 的 # 不算参数。 */
    private List<String> parameters;

    /**
     * 目标语言翻译
     * <li>未翻译时为 null。</li>
     */
    private String text;

    /**
     * 翻译版本
     * <li>未翻译时为 0，保存时按此传 expectedVersion。</li>
     */
    private int version;

    /** 基准已变更：翻译所依据的基准修订号小于当前基准修订号。 */
    private boolean sourceChanged;

    /** 翻译最后修改人账号 ID，十进制字符串；未翻译时为 null。 */
    private String updatedBy;

    /** 翻译最后修改时间，UTC ISO-8601 微秒精度；未翻译时为 null。 */
    private String updateTime;
}
