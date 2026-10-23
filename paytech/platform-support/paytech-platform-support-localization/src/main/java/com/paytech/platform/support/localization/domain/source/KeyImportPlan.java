package com.paytech.platform.support.localization.domain.source;

import java.util.ArrayList;
import java.util.List;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.Sha256;

import lombok.Getter;

/** key 导入的差异计划：四类差异、仅说明变化、未创建的命名空间与问题；预览与确认共用。 */
@Getter
public final class KeyImportPlan {

    /** 新增的 key。 */
    private final List<NewKey> added = new ArrayList<>();

    /** 英文基准变更的 ACTIVE key。 */
    private final List<KeyChange> sourceChanged = new ArrayList<>();

    /** 将废弃的 ACTIVE key。 */
    private final List<I18nKey> deprecated = new ArrayList<>();

    /** 将恢复为 ACTIVE 的 DEPRECATED key（可同时带基准变化）。 */
    private final List<KeyChange> restored = new ArrayList<>();

    /** 只有说明变化的 ACTIVE key。 */
    private final List<KeyChange> descriptionChanged = new ArrayList<>();

    /** 未创建的命名空间编码。 */
    private final List<String> missingNamespaces = new ArrayList<>();

    /** 格式、前缀与 ICU 问题。 */
    private final List<LocalizationProblem> problems = new ArrayList<>();

    /** 基准与状态都不变的 key 数（含只有说明变化的 key）。 */
    private int unchangedCount;

    /** 计数一条没有变化的 key。 */
    void countUnchanged() {
        unchangedCount++;
    }

    /** 是否可以确认导入。 */
    public boolean importable() {
        return missingNamespaces.isEmpty() && problems.isEmpty();
    }

    /** 是否有任何写入。 */
    public boolean hasChanges() {
        return !added.isEmpty() || !sourceChanged.isEmpty() || !deprecated.isEmpty() || !restored.isEmpty()
            || !descriptionChanged.isEmpty();
    }

    /**
     * 预览摘要：由应用、文件内容摘要与 key 集合修订号计算；其中任一变化都使预览过期。
     *
     * @param applicationCode 应用编码
     * @param contentHash 文件内容 SHA-256
     * @param keySetRevision 预览时的 key 集合修订号
     * @return 64 位小写十六进制
     */
    public static String digest(String applicationCode, String contentHash, int keySetRevision) {
        return Sha256.hex("key-import\n" + applicationCode + "\n" + contentHash + "\n" + keySetRevision);
    }
}
