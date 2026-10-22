package com.paytech.card.integration;

import java.util.Map;

/**
 * Platform 国家区域映射端口：BIN 库导入时由发卡国推导发卡区域（技术方案 §3.1：导入时推导并冻结在号段上）。
 * <li>访问外部服务，只能在数据库事务外调用。</li>
 */
public interface PlatformCountryRegionClient {
    /**
     * 查询全部国家区域映射
     *
     * @return 国家代码（ISO 3166-1 alpha-2）→ 区域代码
     */
    Map<String, String> listCountryRegions();
}
