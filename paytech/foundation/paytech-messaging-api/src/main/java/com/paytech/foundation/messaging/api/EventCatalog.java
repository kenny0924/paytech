package com.paytech.foundation.messaging.api;

import java.util.List;
import java.util.Optional;

/**
 * 事件目录读取接口（design/08 §5）。
 *
 * @author Claude
 */
public interface EventCatalog {

    /**
     * 按事件类型查找登记项。
     *
     * @param eventType 事件类型
     * @return 登记项；未登记为空
     */
    Optional<EventCatalogEntry> find(String eventType);

    /**
     * 全部登记项，按目录文件顺序。
     *
     * @return 不可变列表
     */
    List<EventCatalogEntry> entries();
}
