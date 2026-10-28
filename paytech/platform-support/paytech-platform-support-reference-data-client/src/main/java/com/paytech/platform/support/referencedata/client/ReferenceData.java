package com.paytech.platform.support.referencedata.client;

/**
 * 进程内参考数据入口：所有方法只读内存，不发 HTTP、不触发刷新。
 * <li>快照由客户端的调度线程按配置周期从 Platform 全量拉取并按种类原子替换；读方法可以在数据库事务内调用。</li>
 * <li>只能读取 {@code platform-support.reference-data.client.kinds} 中开启的种类，其他种类抛 {@link ReferenceDataUnavailableException}。</li>
 */
public interface ReferenceData {

    /**
     * 币种
     *
     * @return 币种目录
     */
    ReferenceCatalog<ReferenceCurrency> currencies();

    /**
     * 国家与地区
     *
     * @return 国家目录
     */
    ReferenceCatalog<ReferenceCountry> countries();

    /**
     * 一级行政区
     *
     * @return 行政区目录
     */
    ReferenceCatalog<ReferenceSubdivision> subdivisions();

    /**
     * 两级行业分类
     *
     * @return 行业目录
     */
    ReferenceCatalog<ReferenceIndustry> industries();

    /**
     * MCC
     *
     * @return MCC 目录
     */
    ReferenceCatalog<ReferenceMcc> mccs();
}
