package com.paytech.platform.support.localization.service.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.localization.response.SaveTranslationsResponseBody;
import com.paytech.platform.support.api.localization.response.SavedTranslation;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.TranslationWrite;
import com.paytech.platform.support.localization.domain.TranslationWriteContext;
import com.paytech.platform.support.localization.domain.icu.IcuMessageValidator;
import com.paytech.platform.support.localization.domainservice.repository.I18nKeyRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nTranslationRepository;
import com.paytech.platform.support.localization.domainservice.service.TranslationService;
import com.paytech.platform.support.localization.service.processor.request.SaveTranslationsCommand;
import com.paytech.platform.support.localization.service.processor.request.TranslationItem;

import lombok.AllArgsConstructor;

/**
 * 批量保存一个语言的翻译（每次最多 200 条），全部成功或全部失败
 * <li>持应用资源锁；key 须为 ACTIVE（否则 422 VALIDATION_ERROR），文案须通过 ICU 校验（否则 422 I18N_MESSAGE_INVALID，field 为
 * translations[i].text），各条 expectedVersion 须与当前一致（409）。</li>
 * <li>一个本地事务内锁应用行、逐条按版本写入，文案修订号加一；保存后翻译依据当前基准。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class SaveTranslationsProcessor
    extends AbstractLockProcessor<SaveTranslationsCommand, SaveTranslationsResponseBody> {

    private final I18nKeyRepository keys;
    private final TranslationWrites writes;
    private final TranslationService translationService;
    private final I18nTranslationRepository translations;
    private final LocalizationProcessorSupport support;

    @Override
    protected void validate(SaveTranslationsCommand request) {
        Set<String> seen = new HashSet<>();
        for (TranslationItem item : request.getTranslations()) {
            if (!seen.add(item.getKeyCode())) {
                throw new InvalidParameterException("同一 key 不能重复保存: " + item.getKeyCode());
            }
        }
    }

    @Override
    protected LockCommand lockCommand(SaveTranslationsCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.applicationLock(request.getApplicationCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected SaveTranslationsResponseBody processWithinLock(SaveTranslationsCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        writes.requireTranslatable(request.getLocale());
        Map<String, I18nKey> activeKeys = activeKeys(application, request.getTranslations());
        LocalizationProblems.rejectIfAny(keyProblems(request.getTranslations(), activeKeys));
        LocalizationProblems.rejectInvalidMessages(messageProblems(request.getTranslations(), activeKeys));
        List<TranslationWrite> planned = plan(request, activeKeys, support.actorId(session));
        TranslationWriteContext context = new TranslationWriteContext(application, planned, support.now());
        int contentRevision = support.write(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.EDIT_PERMISSION, () -> {
                int revision = translationService.saveTranslations(context);
                support.recordSucceeded(session, LocalizationProcessorSupport.APPLICATION_AUDIT_TYPE,
                    application.getApplicationId(), "保存 " + request.getLocale() + " 翻译 " + planned.size() + " 条");
                return revision;
            });
        return response(planned, contentRevision);
    }

    /** 请求涉及的 ACTIVE key，按完整 key 索引。 */
    Map<String, I18nKey> activeKeys(I18nApplication application, List<TranslationItem> items) {
        return keys.findByCodes(application.getApplicationId(), items.stream().map(TranslationItem::getKeyCode).toList())
            .stream().filter(key -> key.getStatus() == KeyStatus.ACTIVE)
            .collect(Collectors.toMap(I18nKey::getKeyCode, Function.identity()));
    }

    /** key 不存在或已废弃的问题（422 VALIDATION_ERROR），field 为 translations[i].keyCode。 */
    static List<LocalizationProblem> keyProblems(List<TranslationItem> items, Map<String, I18nKey> activeKeys) {
        List<LocalizationProblem> problems = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (!activeKeys.containsKey(items.get(index).getKeyCode())) {
                problems.add(new LocalizationProblem("translations[" + index + "].keyCode",
                    "key 不存在或已废弃: " + items.get(index).getKeyCode()));
            }
        }
        return problems;
    }

    /** ICU 校验问题（422 I18N_MESSAGE_INVALID），field 为 translations[i].text，消息带字符位置；key 已确认全部存在。 */
    static List<LocalizationProblem> messageProblems(List<TranslationItem> items, Map<String, I18nKey> activeKeys) {
        List<LocalizationProblem> problems = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            TranslationItem item = items.get(index);
            String field = "translations[" + index + "].text";
            IcuMessageValidator.validateTranslation(activeKeys.get(item.getKeyCode()).getSourceText(), item.getText())
                .forEach(problem -> problems.add(new LocalizationProblem(field, problem.message())));
        }
        return problems;
    }

    /** 核对各条版本后构造写入；任一不符整体返回 409。 */
    List<TranslationWrite> plan(SaveTranslationsCommand request, Map<String, I18nKey> activeKeys, long actorId) {
        Map<Long, I18nTranslation> existing = translations.findByKeys(activeKeys.values().stream()
            .map(I18nKey::getKeyId).toList(), request.getLocale()).stream()
            .collect(Collectors.toMap(I18nTranslation::getKeyId, Function.identity()));
        List<String> stale = new ArrayList<>();
        List<TranslationWrite> planned = new ArrayList<>();
        for (TranslationItem item : request.getTranslations()) {
            I18nKey key = activeKeys.get(item.getKeyCode());
            I18nTranslation current = existing.get(key.getKeyId());
            int currentVersion = current == null ? 0 : current.getVersion();
            if (currentVersion != item.getExpectedVersion()) {
                stale.add(item.getKeyCode() + "（当前版本 " + currentVersion + "）");
            }
            planned.add(writes.write(key, current, request.getLocale(), item.getText(), actorId));
        }
        if (!stale.isEmpty()) {
            throw new ConcurrentRequestException("翻译已被其他操作修改，请刷新后重试: " + String.join("、", stale));
        }
        return planned;
    }

    /** 各条新版本与文案修订号。 */
    static SaveTranslationsResponseBody response(List<TranslationWrite> planned, int contentRevision) {
        SaveTranslationsResponseBody body = new SaveTranslationsResponseBody();
        body.setTranslations(planned.stream().map(write -> {
            SavedTranslation saved = new SavedTranslation();
            saved.setKeyCode(write.getKey().getKeyCode());
            saved.setVersion(write.getExisting() == null ? 1 : write.getExisting().getVersion());
            return saved;
        }).toList());
        body.setContentRevision(contentRevision);
        return body;
    }
}
