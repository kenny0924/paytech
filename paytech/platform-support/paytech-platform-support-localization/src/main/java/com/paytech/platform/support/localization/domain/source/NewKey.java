package com.paytech.platform.support.localization.domain.source;

/**
 * 导入新增的 key。
 *
 * @param namespaceCode 命名空间编码
 * @param keyCode 完整 key
 * @param entry 文件中的条目
 */
public record NewKey(String namespaceCode, String keyCode, SourceEntry entry) {
}
