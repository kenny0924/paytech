package com.paytech.platform.support.localization.service.processor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.localization.response.ReleaseSummaryResponseBody;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nApplication;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nNamespace;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nRelease;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nReleaseBundle;
import com.paytech.platform.support.localization.core.enums.ReleaseStatus;
import com.paytech.platform.support.localization.domain.LocalizationJson;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.PublishContext;
import com.paytech.platform.support.localization.domain.release.BundleGenerator;
import com.paytech.platform.support.localization.domain.release.CurrentTexts;
import com.paytech.platform.support.localization.domain.release.GeneratedBundle;
import com.paytech.platform.support.localization.domain.release.ReleaseAssessment;
import com.paytech.platform.support.localization.domain.release.ReleaseChangeSummaryFact;
import com.paytech.platform.support.localization.domain.release.ReleaseChanges;
import com.paytech.platform.support.localization.domainservice.repository.I18nNamespaceRepository;
import com.paytech.platform.support.localization.domainservice.repository.I18nReleaseRepository;
import com.paytech.platform.support.localization.domainservice.service.ReleaseService;
import com.paytech.platform.support.localization.integration.ReleaseBundleStore;
import com.paytech.platform.support.localization.service.handler.ReleaseActivationHandler;
import com.paytech.platform.support.localization.service.processor.request.PublishReleaseCommand;

/**
 * 发布当前文案（技术方案 3.3 第 1、2 步），写审计 I18N_RELEASE_PUBLISH
 * <li>持应用资源锁。第 1 步（事务外）：记下文案修订号，读取一次当前文案（ACTIVE key 与翻译），评估覆盖率与 ICU 问题，不达标返回 422；
 * 由发布变更明细还原上一发布号的内容，与当前内容比较得到本次变化行，并计算相对当时线上发布的变更摘要写入发布报告（不读语言包文件）；
 * 生成语言包并经 {@link ReleaseBundleStore} 逐个上传，端口不可用返回 503。</li>
 * <li>第 2 步（短事务）：锁应用行核对文案修订号未变（变了返回 409 I18N_CONTENT_CHANGED，已上传的文件成为孤儿文件），插入
 * PENDING_CHECK 发布（含变化条数）、语言包与变化行，并同事务登记上线补偿任务 I18N_RELEASE_ACTIVATE。</li>
 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PublishReleaseProcessor extends AbstractLockProcessor<PublishReleaseCommand, ReleaseSummaryResponseBody> {

    private final ReleaseService releaseService;
    private final ReleaseChangeSummaries summaries;
    private final I18nReleaseRepository releases;
    private final I18nNamespaceRepository namespaces;
    private final LocalizationProcessorSupport support;
    private final ObjectProvider<ReleaseBundleStore> store;

    /**
     * 装配依赖；语言包存储端口的实现由集成卡提供，缺失时发布返回 503。
     *
     * @param releaseService 发布服务
     * @param summaries 发布内容还原与相对线上发布的变更摘要
     * @param releases 发布仓储
     * @param namespaces 命名空间仓储
     * @param support 公共能力
     * @param store 语言包存储端口
     */
    public PublishReleaseProcessor(ReleaseService releaseService, ReleaseChangeSummaries summaries,
        I18nReleaseRepository releases, I18nNamespaceRepository namespaces, LocalizationProcessorSupport support,
        ObjectProvider<ReleaseBundleStore> store) {
        this.releaseService = releaseService;
        this.summaries = summaries;
        this.releases = releases;
        this.namespaces = namespaces;
        this.support = support;
        this.store = store;
    }

    @Override
    protected LockCommand lockCommand(PublishReleaseCommand request) {
        return LockCommand.tryLock(LocalizationProcessorSupport.applicationLock(request.getApplicationCode()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected ReleaseSummaryResponseBody processWithinLock(PublishReleaseCommand request) {
        SessionContext session = support.authorize(request.getActor(), request.getSession(),
            LocalizationProcessorSupport.PUBLISH_PERMISSION);
        I18nApplication application = support.requireApplication(request.getApplicationCode());
        CurrentTexts texts = support.currentTexts(application);
        ReleaseAssessment assessment = support.assess(application, texts);
        requirePublishable(assessment);
        ReleaseBundleStore bundleStore = requireStore();

        // 本次内容相对上一发布号的变化与相对线上发布的摘要（只查库）
        int releaseNo = releases.maxReleaseNo(application.getApplicationId()) + 1;
        Map<String, Map<String, String>> content = texts.content();
        Map<String, Map<String, String>> previous = summaries.restore(application, releaseNo - 1);
        ReleaseChangeSummaryFact changeSummary = summaries.summarize(application, content, releaseNo - 1, previous);
        I18nRelease release = newRelease(application, releaseNo, assessment, changeSummary, request,
            support.actorId(session));
        ReleaseChanges changes = ReleaseChanges.between(previous, content, application.getApplicationId(), releaseNo,
            release.getCreateTime());
        release.setChangeCount(changes.count());

        // 生成并上传语言包（事务外），再在短事务中登记
        List<I18nReleaseBundle> bundles = upload(bundleStore, application, texts, release);
        PublishContext context = new PublishContext(application, release, bundles, changes.getRows(),
            ReleaseActivationHandler.buildEvent(release.getReleaseId()));
        support.write(request.getActor(), request.getSession(), LocalizationProcessorSupport.PUBLISH_PERMISSION, () -> {
            releaseService.registerRelease(context);
            support.recordRelease(session, release.getReleaseId(), "发布 " + release.getReleaseNo());
            return null;
        });
        return LocalizationResponses.release(release, application.getCurrentReleaseId());
    }

    /** 没有 ACTIVE key 返回 400；覆盖率不足返回 422 并列出缺口；ICU 问题返回 422。 */
    static void requirePublishable(ReleaseAssessment assessment) {
        if (assessment.getActiveKeyCount() == 0) {
            throw new BizCheckFailException("应用没有在用 key，不能发布");
        }
        if (!assessment.insufficient().isEmpty()) {
            List<LocalizationProblem> gaps = assessment.blockingMissing().stream()
                .map(ref -> new LocalizationProblem("missing." + ref.locale(), ref.keyCode() + " 未翻译")).toList();
            throw LocalizationProblems.reject(LocalizationErrorCode.I18N_COVERAGE_INSUFFICIENT, gaps, "missingTotal");
        }
        LocalizationProblems.rejectIfAny(assessment.getProblems());
    }

    /** 语言包存储端口；未接入时 503，不伪造地址。 */
    ReleaseBundleStore requireStore() {
        ReleaseBundleStore available = store.getIfAvailable();
        if (available == null) {
            throw new ServiceUnavailableException();
        }
        return available;
    }

    /** 新发布：PENDING_CHECK，发布号为应用内最大值加一；报告含相对当前发布的变更摘要，变化条数随后填入。 */
    I18nRelease newRelease(I18nApplication application, int releaseNo, ReleaseAssessment assessment,
        ReleaseChangeSummaryFact changeSummary, PublishReleaseCommand request, long actorId) {
        I18nRelease release = new I18nRelease();
        release.setReleaseId(support.nextId());
        release.setApplicationId(application.getApplicationId());
        release.setReleaseNo(releaseNo);
        release.setBasedOnContentRevision(application.getContentRevision());
        release.setStatus(ReleaseStatus.PENDING_CHECK);
        release.setKeyCount(assessment.getActiveKeyCount());
        release.setCoverageJson(LocalizationJson.writeCoverage(assessment.getCoverage()));
        release.setReportJson(LocalizationJson.writeReport(assessment.report(changeSummary)));
        release.setReleaseNote(request.getReleaseNote());
        release.setCreatedBy(actorId);
        release.setCreateTime(support.now());
        release.setUpdateTime(release.getCreateTime());
        return release;
    }

    /** 由同一份当前文案生成并逐个上传语言包（事务外）。 */
    List<I18nReleaseBundle> upload(ReleaseBundleStore bundleStore, I18nApplication application, CurrentTexts texts,
        I18nRelease release) {
        Map<Long, String> namespaceCodes = namespaces.findByApplication(application.getApplicationId()).stream()
            .collect(Collectors.toMap(I18nNamespace::getNamespaceId, I18nNamespace::getNamespaceCode));
        List<GeneratedBundle> generated = BundleGenerator.generate(texts.getActiveKeys(), namespaceCodes,
            texts.getTranslations(), texts.targetLocales());
        return generated.stream().map(bundle -> bundle(release, bundle, bundleStore.store(
            application.getApplicationCode(), release.getReleaseNo(), bundle.getLocale(), bundle.getNamespaceCode(),
            bundle.getContent(), bundle.getSha256()))).toList();
    }

    /** 已上传语言包的登记行。 */
    I18nReleaseBundle bundle(I18nRelease release, GeneratedBundle generated, FileAddress address) {
        I18nReleaseBundle bundle = new I18nReleaseBundle();
        bundle.setBundleId(support.nextId());
        bundle.setReleaseId(release.getReleaseId());
        bundle.setLocaleCode(generated.getLocale());
        bundle.setNamespaceCode(generated.getNamespaceCode());
        bundle.setFileAddress(address.value());
        bundle.setContentHash(generated.getSha256());
        bundle.setByteSize(generated.getContent().length);
        bundle.setCreateTime(release.getCreateTime());
        return bundle;
    }
}
