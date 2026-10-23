package com.paytech.platform.support.localization.service.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.ImportTranslationsResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.TranslationWrite;
import com.paytech.platform.support.localization.domain.TranslationWriteContext;
import com.paytech.platform.support.localization.domain.icu.IcuMessageValidator;
import com.paytech.platform.support.localization.domain.source.TranslationFile;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nTranslationRepository;
import com.paytech.platform.support.localization.domainservice.service.TranslationService;
import com.paytech.platform.support.localization.integration.UploadedFileReader;
import com.paytech.platform.support.localization.service.processor.request.ImportTranslationsCommand;

import lombok.AllArgsConstructor;

/**
 * 导入一个语言的翻译文件（技术方案 3.5）：全部校验通过才整体写入
 * <li>持应用资源锁；事务外读取文件（原因码 I18N_TRANSLATION_IMPORT）。空字符串不改动，与现有翻译相同不写入，未知或已废弃 key、
 * key 不属于所在命名空间整体拒绝（422 VALIDATION_ERROR）；ICU 问题整体拒绝（422 I18N_MESSAGE_INVALID）；各最多列 200 条。</li>
 * <li>有写入时在一个本地事务内锁应用行、按读取时的版本写入，文案修订号加一。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ImportTranslationsProcessor
    extends AbstractLockProcessor<ImportTranslationsCommand, ImportTranslationsResponseBody> {

    private final I18nKeyRepository keys;
    private final UploadedFileReader files;
    private final TranslationWrites writes;
    private final TranslationService translationService;
    private final I18nTranslationRepository translations;
    private final LocalizationProcessorSupport support;

    @Override
    protected LockCommand lockCommand(ImportTranslationsCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.applicationLock(request.getApplicationCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected ImportTranslationsResponseBody processWithinLock(ImportTranslationsCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        writes.requireTranslatable(request.getLocale());
        TranslationFile file = TranslationFile.parse(files.read(request.getFileId(),
            LocalizationCodes.TRANSLATION_IMPORT_REASON).getContent());
        LocalizationProblems.rejectIfAny(headerProblems(file, application, request.getLocale()));
        Outcome outcome = plan(application, file, request.getLocale(), support.actorId(session));
        LocalizationProblems.rejectIfAny(outcome.problems());
        LocalizationProblems.rejectInvalidMessages(outcome.messageProblems());
        TranslationWriteContext context = new TranslationWriteContext(application, outcome.writes(), support.now());
        int contentRevision = support.write(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION, () -> {
                int revision = translationService.saveTranslations(context);
                support.recordSucceeded(session, LocalizationProcessorSupport.APPLICATION_AUDIT_TYPE,
                    application.getApplicationId(), "导入 " + request.getLocale() + " 翻译：写入 "
                        + outcome.writes().size() + "，文件 " + request.getFileId());
                return revision;
            });
        return response(outcome, contentRevision);
    }

    /** 格式问题与文件声明的应用、语言是否一致。 */
    static List<LocalizationProblem> headerProblems(TranslationFile file, I18nApplication application, String locale) {
        List<LocalizationProblem> problems = new ArrayList<>(file.getProblems());
        if (!application.getApplicationCode().equals(file.getApplication())) {
            problems.add(new LocalizationProblem("application", "文件属于应用 " + file.getApplication() + "，不能导入到 "
                + application.getApplicationCode()));
        }
        if (!locale.equals(file.getLocale())) {
            problems.add(new LocalizationProblem("locale", "文件语言 " + file.getLocale() + " 与请求语言 " + locale
                + " 不一致"));
        }
        return problems;
    }

    /** 逐条校验并构造写入。 */
    Outcome plan(I18nApplication application, TranslationFile file, String locale, long actorId) {
        Map<String, I18nKey> active = keys.findByStatus(application.getApplicationId(), KeyStatus.ACTIVE).stream()
            .collect(Collectors.toMap(I18nKey::getKeyCode, Function.identity()));
        Map<Long, I18nTranslation> existing = translations.findByKeys(active.values().stream().map(I18nKey::getKeyId)
            .toList(), locale).stream().collect(Collectors.toMap(I18nTranslation::getKeyId, Function.identity()));
        Outcome outcome = new Outcome(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new int[2]);
        file.getNamespaces().forEach((namespace, entries) -> entries.forEach((keyCode, text) -> classify(outcome,
            namespace, keyCode, text, active.get(keyCode), existing, locale, actorId)));
        return outcome;
    }

    /** 归类一条：空值、问题、未变化或写入。 */
    void classify(Outcome outcome, String namespace, String keyCode, String text, I18nKey key,
        Map<Long, I18nTranslation> existing, String locale, long actorId) {
        String field = "namespaces." + namespace + "." + keyCode;
        if (text.isEmpty()) {
            outcome.counts()[0]++;
            return;
        }
        if (key == null || !namespace.equals(LocalizationCodes.namespaceOf(keyCode))) {
            outcome.problems().add(new LocalizationProblem(field, "key 不存在、已废弃或不属于命名空间 " + namespace));
            return;
        }
        if (text.length() > LocalizationCodes.MAX_TEXT_LENGTH) {
            outcome.problems().add(new LocalizationProblem(field, "翻译不能超过 " + LocalizationCodes.MAX_TEXT_LENGTH
                + " 个字符"));
            return;
        }
        IcuMessageValidator.validateTranslation(key.getSourceText(), text)
            .forEach(problem -> outcome.messageProblems().add(new LocalizationProblem(field, problem.message())));
        I18nTranslation current = existing.get(key.getKeyId());
        if (current != null && current.getText().equals(text)) {
            outcome.counts()[1]++;
            return;
        }
        outcome.writes().add(writes.write(key, current, locale, text, actorId));
    }

    /** 写入、未变化与空值条数。 */
    static ImportTranslationsResponseBody response(Outcome outcome, int contentRevision) {
        ImportTranslationsResponseBody body = new ImportTranslationsResponseBody();
        body.setUpdatedCount(outcome.writes().size());
        body.setUnchangedCount(outcome.counts()[1]);
        body.setEmptyCount(outcome.counts()[0]);
        body.setContentRevision(contentRevision);
        return body;
    }

    /**
     * 校验与归类结果。
     *
     * @param writes 待写入
     * @param problems key 与长度问题（422 VALIDATION_ERROR）
     * @param messageProblems ICU 问题（422 I18N_MESSAGE_INVALID）
     * @param counts [空值条数, 未变化条数]
     */
    record Outcome(List<TranslationWrite> writes, List<LocalizationProblem> problems,
        List<LocalizationProblem> messageProblems, int[] counts) {
    }
}
