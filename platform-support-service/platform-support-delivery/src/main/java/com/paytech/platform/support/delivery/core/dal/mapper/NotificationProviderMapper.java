package com.paytech.platform.support.delivery.core.dal.mapper;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationProvider;
import org.apache.ibatis.annotations.Param;

/** 通知单表 SQL 绑定。 */
public interface NotificationProviderMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationProvider find(@Param("id") long id);
}
