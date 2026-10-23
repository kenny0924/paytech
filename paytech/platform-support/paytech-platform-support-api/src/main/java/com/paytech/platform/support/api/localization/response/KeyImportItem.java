package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 导入预览中的一个 key。 */
@Getter
@Setter
public class KeyImportItem {

    /** 命名空间编码。 */
    private String namespaceCode;

    /** 完整 key。 */
    private String keyCode;

    /**
     * 导入后的英文基准
     * <li>将废弃的 key 为当前基准。</li>
     */
    private String sourceText;

    /**
     * 导入前的英文基准
     * <li>只有基准变更或恢复且文案变化时有值，否则为 null。</li>
     */
    private String previousSourceText;

    /** 开发者说明，可为 null。 */
    private String description;
}
