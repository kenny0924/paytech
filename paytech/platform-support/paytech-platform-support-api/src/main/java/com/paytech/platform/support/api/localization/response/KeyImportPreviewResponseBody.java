package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** key 导入预览：四类差异、未创建的命名空间、问题与预览摘要；不写库。 */
@Getter
@Setter
public class KeyImportPreviewResponseBody {

    /** 是否可以确认导入：没有未创建的命名空间且没有问题。 */
    private boolean importable;

    /** 预览摘要，由文件内容摘要与当前 key 集合修订号计算；确认时原样提交。 */
    private String previewDigest;

    /** 预览时应用的 key 集合修订号。 */
    private int keySetRevision;

    /** 新增的 key。 */
    private List<KeyImportItem> added;

    /** 英文基准变更的 ACTIVE key；其他语言的翻译将标为基准已变更。 */
    private List<KeyImportItem> sourceChanged;

    /** 文件中没有、将标为 DEPRECATED 的 key。 */
    private List<KeyImportItem> deprecated;

    /** 重新出现、将恢复为 ACTIVE 的 key，保留原翻译。 */
    private List<KeyImportItem> restored;

    /** 基准与状态都不变的 key 数（说明变化也计入此类）。 */
    private int unchangedCount;

    /** 文件中出现但尚未创建的命名空间编码。 */
    private List<String> missingNamespaces;

    /** 格式、key 前缀与 ICU 问题。 */
    private List<LocalizationProblem> problems;
}
