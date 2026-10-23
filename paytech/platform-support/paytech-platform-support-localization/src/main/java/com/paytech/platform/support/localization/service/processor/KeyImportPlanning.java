package com.paytech.platform.support.localization.service.processor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.localization.response.KeyImportItem;
import com.paytech.platform.support.api.localization.response.KeyImportPreviewResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.source.KeyChange;
import com.paytech.platform.support.localization.domain.source.KeyImportPlan;
import com.paytech.platform.support.localization.domain.source.KeyImportPlanner;
import com.paytech.platform.support.localization.domain.source.NewKey;
import com.paytech.platform.support.localization.domain.source.SourceFile;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.integration.UploadedFile;
import com.paytech.platform.support.localization.integration.UploadedFileReader;

import lombok.AllArgsConstructor;

/** 预览与确认共用的导入准备：事务外读取已上传文件，按当前命名空间与 key 计算差异与预览摘要。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class KeyImportPlanning {

    private final I18nKeyRepository keys;
    private final UploadedFileReader files;
    private final I18nNamespaceRepository namespaces;

    /**
     * 读取文件并计算差异。
     *
     * @param application 应用；其 key 集合修订号参与预览摘要
     * @param fileId 已上传文件 ID
     * @return 差异、摘要与命名空间
     */
    Planned plan(I18nApplication application, long fileId) {
        UploadedFile file = files.read(fileId, LocalizationCodes.KEY_IMPORT_REASON);
        List<I18nNamespace> created = namespaces.findByApplication(application.getApplicationId());
        KeyImportPlan plan = KeyImportPlanner.plan(SourceFile.parse(file.getContent()),
            application.getApplicationCode(), created.stream().map(I18nNamespace::getNamespaceCode).toList(),
            keys.findByApplication(application.getApplicationId()));
        Map<String, Long> namespaceIds = created.stream()
            .collect(Collectors.toMap(I18nNamespace::getNamespaceCode, I18nNamespace::getNamespaceId));
        String digest = KeyImportPlan.digest(application.getApplicationCode(), file.getContentHash(),
            application.getKeySetRevision());
        return new Planned(plan, digest, namespaceIds);
    }

    /**
     * 预览响应。
     *
     * @param planned 差异
     * @param keySetRevision 预览时的 key 集合修订号
     * @return 响应体
     */
    static KeyImportPreviewResponseBody preview(Planned planned, int keySetRevision) {
        KeyImportPlan plan = planned.plan();
        KeyImportPreviewResponseBody body = new KeyImportPreviewResponseBody();
        body.setImportable(plan.importable());
        body.setPreviewDigest(planned.digest());
        body.setKeySetRevision(keySetRevision);
        body.setAdded(plan.getAdded().stream().map(KeyImportPlanning::item).toList());
        body.setSourceChanged(plan.getSourceChanged().stream().map(KeyImportPlanning::item).toList());
        body.setDeprecated(plan.getDeprecated().stream().map(KeyImportPlanning::item).toList());
        body.setRestored(plan.getRestored().stream().map(KeyImportPlanning::item).toList());
        body.setUnchangedCount(plan.getUnchangedCount());
        body.setMissingNamespaces(List.copyOf(plan.getMissingNamespaces()));
        body.setProblems(plan.getProblems().stream().map(LocalizationResponses::problem).toList());
        return body;
    }

    /** 新增 key。 */
    static KeyImportItem item(NewKey key) {
        return item(key.namespaceCode(), key.keyCode(), key.entry().text(), null, key.entry().description());
    }

    /** 基准变更或恢复的 key；基准未变时不带旧基准。 */
    static KeyImportItem item(KeyChange change) {
        I18nKey key = change.key();
        return item(LocalizationCodes.namespaceOf(key.getKeyCode()), key.getKeyCode(), change.entry().text(),
            change.sourceChanged() ? key.getSourceText() : null, change.entry().description());
    }

    /** 将废弃的 key。 */
    static KeyImportItem item(I18nKey key) {
        return item(LocalizationCodes.namespaceOf(key.getKeyCode()), key.getKeyCode(), key.getSourceText(), null,
            key.getDescription());
    }

    /** 组装一项。 */
    static KeyImportItem item(String namespace, String keyCode, String text, String previous, String description) {
        KeyImportItem item = new KeyImportItem();
        item.setNamespaceCode(namespace);
        item.setKeyCode(keyCode);
        item.setSourceText(text);
        item.setPreviousSourceText(previous);
        item.setDescription(description);
        return item;
    }

    /**
     * 计算结果。
     *
     * @param plan 差异
     * @param digest 预览摘要
     * @param namespaceIds 已创建的命名空间编码到 ID
     */
    record Planned(KeyImportPlan plan, String digest, Map<String, Long> namespaceIds) {
    }
}
