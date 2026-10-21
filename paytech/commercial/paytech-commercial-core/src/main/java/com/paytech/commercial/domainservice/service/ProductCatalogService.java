package com.paytech.commercial.domainservice.service;

import com.paytech.commercial.domain.ProductCatalogContext;

/**
 * 产品目录的本地保存：每个方法一个本地事务，写目录行并推进目录 BCR Root（首次写入时创建）与快照
 * <li>接收已在目录锁内校验的 Context；目录行以 change_version 做 CAS，影响行数不符整体回滚。</li>
 * <li>不访问外部服务、Redis 或 MQ。</li>
 */
public interface ProductCatalogService {
    /**
     * 新增可开通项与其可配维度。
     *
     * @param context 含新可开通项与维度行
     */
    void createCapability(ProductCatalogContext context);

    /**
     * 改写可开通项名称、归属产品并整体替换可配维度。
     *
     * @param context 含已读取的可开通项、目标值与新维度行
     */
    void updateCapability(ProductCatalogContext context);

    /**
     * 停用或启用可开通项。
     *
     * @param context 含已读取的可开通项、其维度与目标状态
     */
    void transitionCapability(ProductCatalogContext context);

    /**
     * 改写计费事件挂接的开通项。
     *
     * @param context 含已读取的计费事件与目标挂接
     */
    void updateFeeEventCapability(ProductCatalogContext context);
}
