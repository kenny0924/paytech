package com.paytech.platform.support.delivery.domainservice.repository;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;

/** NotificationProvider 单表仓储。 */
public interface NotificationProviderRepository {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationProvider find(long id);
}
