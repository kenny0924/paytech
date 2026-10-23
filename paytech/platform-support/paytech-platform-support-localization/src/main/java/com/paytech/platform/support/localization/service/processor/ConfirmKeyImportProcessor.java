package com.paytech.platform.support.localization.service.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.api.localization.response.KeyImportResultResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.KeyImportContext;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.source.KeyImportPlan;
import com.paytech.platform.support.localization.domain.source.NewKey;
import com.paytech.platform.support.localization.domainservice.service.KeyImportService;
import com.paytech.platform.support.localization.service.processor.KeyImportPlanning.Planned;
import com.paytech.platform.support.localization.service.processor.request.KeyImportCommand;

import lombok.AllArgsConstructor;

/**
 * 确认 key 导入（技术方案 3.5）
 * <li>持应用资源锁；事务外读取文件并重算差异，摘要不符（期间有人导入过）返回 409 I18N_IMPORT_PREVIEW_STALE；仍有未创建的命名空间返回 422
 * I18N_NAMESPACE_NOT_FOUND，其他问题返回 422 VALIDATION_ERROR。</li>
 * <li>一个本地事务内锁应用行、核对 key 集合修订号与命名空间后整体写入，key 集合与文案修订号各加一。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ConfirmKeyImportProcessor extends AbstractLockProcessor<KeyImportCommand, KeyImportResultResponseBody> {

    private final KeyImportService imports;
    private final KeyImportPlanning planning;
    private final LocalizationProcessorSupport support;

    @Override
    protected void validate(KeyImportCommand request) {
        if (request.getPreviewDigest() == null) {
            throw new InvalidParameterException("确认导入须提供 previewDigest");
        }
    }

    @Override
    protected LockCommand lockCommand(KeyImportCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.applicationLock(request.getApplicationCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected KeyImportResultResponseBody processWithinLock(KeyImportCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        Planned planned = planning.plan(application, request.getFileId());
        requireImportable(planned, request.getPreviewDigest());
        KeyImportContext context = new KeyImportContext(application, planned.plan(),
            newKeys(application, planned, support.actorId(session)), referencedNamespaces(planned),
            support.actorId(session), support.now());
        support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.EDIT_PERMISSION, () -> {
            imports.importKeys(context);
            KeyImportPlan plan = planned.plan();
            support.recordSucceeded(session, LocalizationProcessorSupport.APPLICATION_AUDIT_TYPE,
                application.getApplicationId(), "导入 key：新增 " + plan.getAdded().size() + "，基准变更 "
                    + plan.getSourceChanged().size() + "，废弃 " + plan.getDeprecated().size() + "，恢复 "
                    + plan.getRestored().size());
            return null;
        });
        return result(planned.plan(), application);
    }

    /** 摘要一致且没有问题。 */
    static void requireImportable(Planned planned, String previewDigest) {
        if (!planned.digest().equals(previewDigest)) {
            throw new FailException(LocalizationErrorCode.I18N_IMPORT_PREVIEW_STALE);
        }
        KeyImportPlan plan = planned.plan();
        if (!plan.getMissingNamespaces().isEmpty()) {
            List<LocalizationProblem> missing = plan.getMissingNamespaces().stream()
                .map(code -> new LocalizationProblem("namespaces." + code, "命名空间尚未创建: " + code)).toList();
            throw LocalizationProblems.reject(LocalizationErrorCode.I18N_NAMESPACE_NOT_FOUND, missing, null);
        }
        LocalizationProblems.rejectIfAny(plan.getProblems());
    }

    /** 新 key 所在命名空间的编码与 ID；事务内只重证这些命名空间。 */
    static Map<String, Long> referencedNamespaces(Planned planned) {
        Map<String, Long> referenced = new HashMap<>();
        planned.plan().getAdded().forEach(added -> referenced.put(added.namespaceCode(),
            planned.namespaceIds().get(added.namespaceCode())));
        return referenced;
    }

    /** 新 key：ACTIVE、基准修订号 1、版本 1。 */
    List<I18nKey> newKeys(I18nApplication application, Planned planned, long actorId) {
        return planned.plan().getAdded().stream().map(added -> newKey(application, planned, added, actorId)).toList();
    }

    /** 一个新 key。 */
    I18nKey newKey(I18nApplication application, Planned planned, NewKey added, long actorId) {
        I18nKey key = new I18nKey();
        key.setKeyId(support.nextId());
        key.setApplicationId(application.getApplicationId());
        key.setNamespaceId(planned.namespaceIds().get(added.namespaceCode()));
        key.setKeyCode(added.keyCode());
        key.setSourceText(added.entry().text());
        key.setDescription(added.entry().description());
        key.setSourceRevision(1);
        key.setStatus(KeyStatus.ACTIVE);
        key.setVersion(1);
        key.setCreatedBy(actorId);
        key.setUpdatedBy(actorId);
        key.setCreateTime(support.now());
        key.setUpdateTime(key.getCreateTime());
        return key;
    }

    /** 结果：修订号按条件更新各加一。 */
    static KeyImportResultResponseBody result(KeyImportPlan plan, I18nApplication application) {
        KeyImportResultResponseBody body = new KeyImportResultResponseBody();
        body.setAddedCount(plan.getAdded().size());
        body.setSourceChangedCount(plan.getSourceChanged().size());
        body.setDeprecatedCount(plan.getDeprecated().size());
        body.setRestoredCount(plan.getRestored().size());
        body.setDescriptionChangedCount(plan.getDescriptionChanged().size());
        body.setKeySetRevision(application.getKeySetRevision() + 1);
        body.setContentRevision(application.getContentRevision() + 1);
        return body;
    }
}
