package com.paytech.platform.support.delivery.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.delivery.core.dal.dataobject.NotificationTemplateContent;

/** 通知单表 SQL 绑定。 */
public interface NotificationTemplateContentMapper {

    /** 读取本表的准确身份，缺失返回空。 */
    NotificationTemplateContent find(@Param("id") long id);

    /** 读取精确语言内容，不执行语言回退。 */
    NotificationTemplateContent findLocale(@Param("templateId") long templateId, @Param("locale") String locale);

    /** 按语言升序读取一组模板的全部内容。 */
    List<NotificationTemplateContent> findTemplates(@Param("templateIds") List<Long> templateIds);

    /** 统计模板已有语言数。 */
    long countTemplate(@Param("templateId") long templateId);

    /** 插入新语言内容。 */
    int insert(NotificationTemplateContent item);

    /** 按原版本替换标题与正文。 */
    int update(@Param("item") NotificationTemplateContent item, @Param("subject") String subject,
        @Param("body") String body, @Param("now") Instant now);
}
