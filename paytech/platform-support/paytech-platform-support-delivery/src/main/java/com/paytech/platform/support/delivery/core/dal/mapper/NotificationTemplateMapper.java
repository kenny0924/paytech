package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplate;
import com.paytech.platform.support.delivery.core.enums.NotificationConfigurationStatus;

/** 通知单表 SQL 绑定。 */
public interface NotificationTemplateMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationTemplate find(@Param("id") long id);

    /** 按唯一模板编码读取当前定义。 */
    NotificationTemplate findCode(@Param("code") String code);

    /** 按身份批量读取，供列表引用展示。 */
    List<NotificationTemplate> findIds(@Param("ids") List<Long> ids);

    /** 按编码升序分页；keyword 已转义为 LIKE 模式。 */
    List<NotificationTemplate> page(@Param("keyword") String keyword,
        @Param("status") NotificationConfigurationStatus status, @Param("offset") long offset,
        @Param("size") int size);

    /** 与分页相同条件的总数。 */
    long count(@Param("keyword") String keyword, @Param("status") NotificationConfigurationStatus status);

    /** 插入新模板定义。 */
    int insert(NotificationTemplate item);

    /** 按原版本切换启停状态。 */
    int changeStatus(@Param("item") NotificationTemplate item,
        @Param("status") NotificationConfigurationStatus status, @Param("now") Instant now);
}
