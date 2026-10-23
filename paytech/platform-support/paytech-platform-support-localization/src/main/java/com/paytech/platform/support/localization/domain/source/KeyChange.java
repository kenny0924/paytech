package com.paytech.platform.support.localization.domain.source;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;

/**
 * 导入对已有 key 的一次修改。
 *
 * @param key 已有 key
 * @param entry 文件中的新条目
 * @param sourceChanged 英文基准是否变化
 */
public record KeyChange(I18nKey key, SourceEntry entry, boolean sourceChanged) {
}
