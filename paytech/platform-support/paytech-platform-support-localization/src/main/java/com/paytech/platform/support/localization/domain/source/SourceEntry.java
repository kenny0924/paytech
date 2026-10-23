package com.paytech.platform.support.localization.domain.source;

/**
 * 导入文件中的一个 key：英文基准与开发者说明。
 *
 * @param text 英文基准
 * @param description 开发者说明；未提供为 null
 */
public record SourceEntry(String text, String description) {
}
