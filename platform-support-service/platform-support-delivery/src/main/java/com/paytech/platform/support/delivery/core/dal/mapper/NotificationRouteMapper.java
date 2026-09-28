package com.paytech.platform.support.delivery.core.dal.mapper;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import org.apache.ibatis.annotations.Param;

/** 通知单表 SQL 绑定。 */
public interface NotificationRouteMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationRoute find(@Param("id") long id);

    /** 读取模板唯一的当前路由。 */
    NotificationRoute findTemplate(@Param("templateId") long templateId);
}
