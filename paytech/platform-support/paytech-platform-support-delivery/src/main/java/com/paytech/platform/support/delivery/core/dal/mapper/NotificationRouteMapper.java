package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationRoute;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

/** 通知单表 SQL 绑定。 */
public interface NotificationRouteMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationRoute find(@Param("id") long id);

    /** 读取模板唯一的当前路由。 */
    NotificationRoute findTemplate(@Param("templateId") long templateId);

    /** 读取一组模板的路由。 */
    List<NotificationRoute> findTemplates(@Param("templateIds") List<Long> templateIds);

    /** 读取以一组账号为主或备用账号的路由。 */
    List<NotificationRoute> findProviders(@Param("providerIds") List<Long> providerIds);

    /** 插入新路由。 */
    int insert(NotificationRoute item);

    /** 按原版本替换主备账号。 */
    int changeProviders(@Param("item") NotificationRoute item, @Param("primary") long primary,
        @Param("fallback") Long fallback, @Param("now") Instant now);

    /** 按原版本切换启停状态。 */
    int changeStatus(@Param("item") NotificationRoute item, @Param("status") NotificationConfigurationStatus status,
        @Param("now") Instant now);
}
