package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 相对线上发布的变更摘要：key 变化按 en 计，翻译变化按非 en 语言计；示例 key 各最多 20 个，按字典序。 */
@Getter
@Setter
public class ReleaseChangeSummary {

    /**
     * 比较基准的发布号
     * <li>没有线上发布时为 null，此时与空内容比较（全部计为新增）。</li>
     */
    private Integer baseReleaseNo;

    /** 新增 key 数。 */
    private int addedKeyCount;

    /** 新增 key 示例。 */
    private List<String> addedKeys;

    /** 废弃（不再发布）的 key 数。 */
    private int deprecatedKeyCount;

    /** 废弃 key 示例。 */
    private List<String> deprecatedKeys;

    /** 英文基准变化的 key 数。 */
    private int sourceChangedKeyCount;

    /** 英文基准变化的 key 示例。 */
    private List<String> sourceChangedKeys;

    /** 各非 en 语言的翻译变化，按语言代码排列；没有变化的语言不出现。 */
    private List<LocaleTranslationChange> translationChanges;
}
