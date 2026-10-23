package com.paytech.platform.support.localization.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.localization.response.KeyImportPreviewResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.service.processor.request.KeyImportCommand;

import lombok.AllArgsConstructor;

/**
 * key 导入预览：复核 I18N_EDIT，事务外读取已上传文件，返回四类差异、未创建的命名空间、问题与预览摘要；不写库、不加锁。
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PreviewKeyImportProcessor extends AbstractProcessor<KeyImportCommand, KeyImportPreviewResponseBody> {

    private final KeyImportPlanning planning;
    private final LocalizationProcessorSupport support;

    @Override
    protected KeyImportPreviewResponseBody processRequest(KeyImportCommand request) {
        support.authorize(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        return KeyImportPlanning.preview(planning.plan(application, request.getFileId()),
            application.getKeySetRevision());
    }
}
