package com.paytech.client.master.integration;

import java.util.Collection;
import java.util.Map;

import com.paytech.client.master.domain.onboarding.pack.ReferenceDataItem;

/**
 * Platform 参考数据目录出站端口：只按编码批量查询，不拉全量、不缓存；只能在数据库事务外调用
 * <li>格式不合法的编码不发往 Platform，视为不存在；Platform 未返回的编码同样不出现在结果中，由调用方判断。</li>
 * <li>Platform 不可用、拒绝或响应不完整时失败关闭为 ONBOARDING_DEPENDENCY_UNAVAILABLE（503）。</li>
 */
public interface ReferenceDataClient {
    /**
     * 查询国家与地区
     *
     * @param codes ISO 3166-1 alpha-2 国家代码
     * @return 编码到条目，只含 Platform 返回的编码
     */
    Map<String, ReferenceDataItem> countries(Collection<String> codes);

    /**
     * 查询币种
     *
     * @param codes ISO 4217 币种代码
     * @return 编码到条目，只含 Platform 返回的编码
     */
    Map<String, ReferenceDataItem> currencies(Collection<String> codes);

    /**
     * 查询一级行政区
     *
     * @param codes ISO 3166-2 行政区代码，如 AE-DU
     * @return 编码到条目（带所属国家），只含 Platform 返回的编码
     */
    Map<String, ReferenceDataItem> subdivisions(Collection<String> codes);

    /**
     * 查询行业
     *
     * @param codes 行业编码
     * @return 编码到条目（带层级），只含 Platform 返回的编码
     */
    Map<String, ReferenceDataItem> industries(Collection<String> codes);

    /**
     * 查询 MCC
     *
     * @param codes 四位 MCC
     * @return 编码到条目，只含 Platform 返回的编码
     */
    Map<String, ReferenceDataItem> mccs(Collection<String> codes);
}
