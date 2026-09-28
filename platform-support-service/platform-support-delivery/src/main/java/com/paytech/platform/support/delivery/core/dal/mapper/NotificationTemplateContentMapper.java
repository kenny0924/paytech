package com.paytech.platform.support.delivery.core.dal.mapper;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplateContent;
import org.apache.ibatis.annotations.Param;

/** 通知单表 SQL 绑定。 */
public interface NotificationTemplateContentMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationTemplateContent find(@Param("id") long id);

    /** 读取精确语言内容，不执行语言回退。 */
    NotificationTemplateContent findLocale(@Param("templateId") long templateId, @Param("locale") String locale);
}
