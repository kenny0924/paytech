package com.paytech.platform.support.localization.domainservice.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.KeyImportContext;
import com.paytech.platform.support.localization.domain.source.KeyChange;
import com.paytech.platform.support.localization.domain.source.KeyImportPlan;
import com.paytech.platform.support.localization.domainservice.repository.I18nApplicationRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.domainservice.service.KeyImportService;

import lombok.AllArgsConstructor;

/** 导入写入：应用行锁与命名空间删除、其他导入、翻译保存串行；每个 key 按原版本条件更新。 */
@Service
@AllArgsConstructor
public class KeyImportServiceImpl implements KeyImportService {

    private final I18nKeyRepository keys;
    private final I18nNamespaceRepository namespaces;
    private final I18nApplicationRepository applications;

    /** 重证后写入四类差异与说明变化，再推进修订号。 */
    @Override
    public void importKeys(KeyImportContext context) {
        I18nApplication application = lockCurrent(context.getApplication());
        requireNamespaces(application.getApplicationId(), context.getNamespaceIds());
        KeyImportPlan plan = context.getPlan();
        keys.insertAll(context.getNewKeys());
        plan.getSourceChanged().forEach(change -> apply(change, KeyStatus.ACTIVE, context));
        plan.getRestored().forEach(change -> apply(change, KeyStatus.ACTIVE, context));
        plan.getDescriptionChanged().forEach(change -> apply(change, KeyStatus.ACTIVE, context));
        plan.getDeprecated().forEach(key -> keys.updateImported(key, next(key, key.getSourceText(),
            key.getDescription(), KeyStatus.DEPRECATED, context)));
        applications.advanceKeySet(application, context.getNow());
    }

    /** 锁应用行，核对预览依据的 key 集合修订号。 */
    I18nApplication lockCurrent(I18nApplication expected) {
        I18nApplication current = applications.lockById(expected.getApplicationId());
        if (current == null) {
            throw new ConcurrentRequestException("应用已不存在: " + expected.getApplicationCode());
        }
        if (!Objects.equals(current.getKeySetRevision(), expected.getKeySetRevision())) {
            throw new FailException(LocalizationErrorCode.I18N_IMPORT_PREVIEW_STALE);
        }
        return current;
    }

    /** 计划引用的命名空间仍存在且是同一行。 */
    void requireNamespaces(long applicationId, Map<String, Long> expected) {
        Map<String, Long> current = namespaces.findByApplication(applicationId).stream()
            .collect(Collectors.toMap(I18nNamespace::getNamespaceCode, I18nNamespace::getNamespaceId));
        List<ProblemFieldError> missing = expected.entrySet().stream()
            .filter(entry -> !entry.getValue().equals(current.get(entry.getKey())))
            .map(entry -> new ProblemFieldError("namespaces." + entry.getKey(), "命名空间尚未创建: " + entry.getKey()))
            .toList();
        if (!missing.isEmpty()) {
            throw new RequestBodyValidationException(LocalizationErrorCode.I18N_NAMESPACE_NOT_FOUND, missing, null);
        }
    }

    /** 写入文件中的基准与说明；基准变化时基准修订号加一。 */
    void apply(KeyChange change, KeyStatus status, KeyImportContext context) {
        I18nKey key = change.key();
        keys.updateImported(key, next(key, change.entry().text(), change.entry().description(), status, context));
    }

    /** 目标导入字段。 */
    static I18nKey next(I18nKey key, String sourceText, String description, KeyStatus status,
        KeyImportContext context) {
        I18nKey next = new I18nKey();
        next.setSourceText(sourceText);
        next.setDescription(description);
        boolean changed = !Objects.equals(key.getSourceText(), sourceText);
        next.setSourceRevision(changed ? key.getSourceRevision() + 1 : key.getSourceRevision());
        next.setStatus(status);
        next.setUpdatedBy(context.getActorId());
        next.setUpdateTime(context.getNow());
        return next;
    }
}
