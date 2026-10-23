package com.paytech.platform.support.localization.domain.source;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.icu.IcuMessageValidator;

/**
 * key 导入差异计算（技术方案 3.5、需求 B1）：文件代表应用完整的 key 集合。纯 Java。
 * <li>文件中有而库中没有为新增；ACTIVE 且基准变化为基准变更；ACTIVE 而文件中没有为将废弃；DEPRECATED 而文件中重新出现为恢复。</li>
 * <li>命名空间不会被导入创建或删除；文件中出现但未创建的命名空间使导入不可确认。英文基准须通过 ICU 语法与 other 分支校验。</li>
 */
public final class KeyImportPlanner {

    private KeyImportPlanner() {
    }

    /**
     * 计算差异。
     *
     * @param file 已解析的导入文件
     * @param applicationCode 目标应用编码
     * @param namespaceCodes 应用已创建的命名空间编码
     * @param existing 应用的全部 key（含已废弃）
     * @return 差异计划
     */
    public static KeyImportPlan plan(SourceFile file, String applicationCode, Collection<String> namespaceCodes,
        List<I18nKey> existing) {
        KeyImportPlan plan = new KeyImportPlan();
        plan.getProblems().addAll(file.getProblems());
        if (file.getApplication() != null && !applicationCode.equals(file.getApplication())) {
            plan.getProblems().add(new LocalizationProblem("application", "文件属于应用 " + file.getApplication()
                + "，不能导入到 " + applicationCode));
        }
        Map<String, I18nKey> current = new HashMap<>();
        existing.forEach(key -> current.put(key.getKeyCode(), key));
        Set<String> created = Set.copyOf(namespaceCodes);
        file.getNamespaces().forEach((namespace, entries) -> {
            if (!created.contains(namespace)) {
                plan.getMissingNamespaces().add(namespace);
            }
            entries.forEach((keyCode, entry) -> classify(plan, namespace, keyCode, entry, current.remove(keyCode)));
        });
        current.values().stream().filter(key -> key.getStatus() == KeyStatus.ACTIVE)
            .forEach(key -> plan.getDeprecated().add(key));
        return plan;
    }

    /** 归类文件中的一个 key，并校验其英文基准。 */
    static void classify(KeyImportPlan plan, String namespace, String keyCode, SourceEntry entry, I18nKey key) {
        IcuMessageValidator.validateSource(entry.text()).forEach(problem -> plan.getProblems().add(
            new LocalizationProblem("namespaces." + namespace + "." + keyCode, problem.message())));
        if (key == null) {
            plan.getAdded().add(new NewKey(namespace, keyCode, entry));
            return;
        }
        boolean sourceChanged = !Objects.equals(key.getSourceText(), entry.text());
        KeyChange change = new KeyChange(key, entry, sourceChanged);
        if (key.getStatus() == KeyStatus.DEPRECATED) {
            plan.getRestored().add(change);
        } else if (sourceChanged) {
            plan.getSourceChanged().add(change);
        } else {
            classifyUnchanged(plan, change);
        }
    }

    /** 基准未变的 ACTIVE key：说明变化单独记录，均计入未变化数。 */
    static void classifyUnchanged(KeyImportPlan plan, KeyChange change) {
        if (!Objects.equals(change.key().getDescription(), change.entry().description())) {
            plan.getDescriptionChanged().add(change);
        }
        plan.countUnchanged();
    }
}
