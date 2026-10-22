package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.filedocument.domain.file.FileSourceCodes;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.domain.action.ActionConfig;
import com.paytech.platform.support.workflow.domain.action.FileUploadActionConfig;
import com.paytech.platform.support.workflow.domain.action.FormActionConfig;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.form.FormField;

import tools.jackson.databind.JsonNode;

/**
 * 工作流附件规则（技术方案 3.2、3.3 与第 5 节）：FILE 值为文件服务签发的文件地址（{@code /files/v1/{fileId}}）字符串数组，
 * 业务数据只存地址、不存文件 ID；附件归属不建表，由启动值与动作输出中的地址表达。地址由 {@link FileAddress} 解析，
 * 解析出的文件 ID 只在内存中用于查询文件事实。纯内存判断，文件事实由调用方事先查询。
 *
 * <ul>
 * <li>归属：文件存在、来源系统为 platform-support、上传原因为 WORKFLOW_ATTACHMENT、上传人为提交人。不满足直接拒绝，不保存，
 * 防止把他人的文件地址写进实例后借下载授权读取。</li>
 * <li>内容：数量、MIME、扫描状态与对象状态。发起时扫描中直接拒绝；动作提交时记为验证未通过，扫描结束后重新验证。</li>
 * </ul>
 */
public final class AttachmentRules {

    /** 工作流附件的上传原因代码。 */
    public static final String REASON_CODE = "WORKFLOW_ATTACHMENT";

    /** 工作流附件的来源系统：Platform 内部模块调用文件入口时使用的来源系统。 */
    public static final String SOURCE_SYSTEM = FileSourceCodes.PLATFORM_SUPPORT;

    /** 发起时文件仍在扫描的拒绝原因。 */
    public static final String SCANNING_REJECTION = "文件扫描中，请稍后再提交";

    private AttachmentRules() {
    }

    /**
     * 解析 FILE 值中的文件地址，返回文件 ID。
     *
     * @param value FILE 值
     * @return 文件 ID（保持顺序，可能重复）；缺失或空值为空列表；不是文件地址字符串数组时为 null
     */
    public static List<Long> fileIds(JsonNode value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : value) {
            FileAddress address = address(item);
            if (address == null) {
                return null;
            }
            ids.add(address.fileId());
        }
        return ids;
    }

    /**
     * 文件地址文本。
     *
     * @param fileId 文件 ID
     * @return {@code /files/v1/{fileId}}
     */
    public static String address(long fileId) {
        return FileAddress.of(fileId).value();
    }

    /**
     * 表单 FILE 字段的地址格式错误：值不是数组，或元素不是合法地址；空值不在此报告（由必填规则处理）。
     *
     * @param form 字段定义
     * @param values 提交值
     * @param root 提交值在请求体中的路径，如 startValues、output
     * @return 请求体字段路径（如 startValues.contractFiles[0]）到原因
     */
    public static Map<String, String> addressErrors(FormDefinition form, JsonNode values, String root) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (form == null || values == null || !values.isObject()) {
            return errors;
        }
        for (FormField field : form.fields()) {
            if (field.type() == FormFieldType.FILE) {
                valueAddressErrors(values.get(field.key()), root + "." + field.key(), errors);
            }
        }
        return errors;
    }

    /**
     * 动作输出的地址格式错误：FILE_UPLOAD 的 files 与 FORM 的 FILE 字段。
     *
     * @param config 动作配置
     * @param output 提交内容
     * @param root 提交内容在请求体中的路径
     * @return 请求体字段路径到原因
     */
    public static Map<String, String> addressErrors(ActionConfig config, JsonNode output, String root) {
        if (output == null || !output.isObject()) {
            return new LinkedHashMap<>();
        }
        return switch (config) {
            case FileUploadActionConfig upload -> {
                Map<String, String> errors = new LinkedHashMap<>();
                valueAddressErrors(output.get(FileUploadActionConfig.FILES_OUTPUT),
                    root + "." + FileUploadActionConfig.FILES_OUTPUT, errors);
                yield errors;
            }
            case FormActionConfig form -> addressErrors(form.form(), output, root);
            default -> new LinkedHashMap<>();
        };
    }

    /**
     * 一个 FILE 值的地址格式错误。
     *
     * @param value FILE 值
     * @param path 字段路径
     * @param errors 收集结果
     */
    static void valueAddressErrors(JsonNode value, String path, Map<String, String> errors) {
        if (value == null || value.isNull() || value.isString() && value.asString().isEmpty()) {
            return;
        }
        if (!value.isArray()) {
            errors.put(path, "必须是文件地址数组");
            return;
        }
        for (int index = 0; index < value.size(); index++) {
            if (address(value.get(index)) == null) {
                errors.put(path + "[" + index + "]", "文件地址格式不合法，应为 /files/v1/{fileId}");
            }
        }
    }

    /**
     * 解析一个地址元素。
     *
     * @param item 元素
     * @return 地址；不是合法地址字符串为 null
     */
    static FileAddress address(JsonNode item) {
        if (item == null || !item.isString()) {
            return null;
        }
        try {
            return FileAddress.parse(item.asString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /**
     * 表单 FILE 字段引用的文件；形态不合法的值跳过，由字段校验报告。
     *
     * @param form 字段定义
     * @param values 提交值
     * @return 文件 ID
     */
    public static Set<Long> referenced(FormDefinition form, JsonNode values) {
        Set<Long> ids = new LinkedHashSet<>();
        if (form == null || values == null || !values.isObject()) {
            return ids;
        }
        for (FormField field : form.fields()) {
            if (field.type() == FormFieldType.FILE) {
                ids.addAll(Objects.requireNonNullElse(fileIds(values.get(field.key())), List.of()));
            }
        }
        return ids;
    }

    /**
     * 动作输出引用的文件：FILE_UPLOAD 取 files，FORM 取 FILE 字段，其他类型没有文件。
     *
     * @param config 动作配置
     * @param output 提交内容
     * @return 文件 ID
     */
    public static Set<Long> referenced(ActionConfig config, JsonNode output) {
        if (output == null || !output.isObject()) {
            return new LinkedHashSet<>();
        }
        return switch (config) {
            case FileUploadActionConfig upload -> new LinkedHashSet<>(Objects.requireNonNullElse(
                fileIds(output.get(FileUploadActionConfig.FILES_OUTPUT)), List.of()));
            case FormActionConfig form -> referenced(form.form(), output);
            default -> new LinkedHashSet<>();
        };
    }

    /**
     * 动作是否接收文件：FILE_UPLOAD，或含 FILE 字段的 FORM。
     *
     * @param config 动作配置
     * @return 接收文件返回 true
     */
    public static boolean acceptsFiles(ActionConfig config) {
        return switch (config) {
            case FileUploadActionConfig upload -> true;
            case FormActionConfig form -> form.form().fields().stream().anyMatch(f -> f.type() == FormFieldType.FILE);
            default -> false;
        };
    }

    /**
     * 归属问题：文件不存在、不是工作流附件或不是提交人上传。
     *
     * @param ids 引用的文件
     * @param facts 文件事实；查不到的文件不在其中
     * @param submitter 提交人账号
     * @return 问题；没有问题为空
     */
    public static List<String> ownershipProblems(Collection<Long> ids, Map<Long, AttachmentFact> facts,
        long submitter) {
        List<String> problems = new ArrayList<>();
        for (Long id : ids) {
            AttachmentFact fact = facts.get(id);
            if (fact == null) {
                problems.add("文件不存在：" + address(id));
            } else if (!SOURCE_SYSTEM.equals(fact.sourceSystem()) || !REASON_CODE.equals(fact.reasonCode())) {
                problems.add("不是工作流附件：" + address(id));
            } else if (!Long.valueOf(submitter).equals(fact.uploaderId())) {
                problems.add("文件不是提交人本人上传：" + address(id));
            }
        }
        return problems;
    }

    /**
     * 是否有文件仍在扫描。
     *
     * @param ids 引用的文件
     * @param facts 文件事实
     * @return 有扫描中的文件返回 true
     */
    public static boolean anyScanning(Collection<Long> ids, Map<Long, AttachmentFact> facts) {
        return ids.stream().map(facts::get).anyMatch(fact -> fact != null && fact.scanning());
    }

    /**
     * 一个 FILE 值的内容问题：形态、重复、数量、类型与扫描状态。
     *
     * @param value 非空的 FILE 值
     * @param minFiles 最少文件数；为空不限
     * @param maxFiles 最多文件数；为空不限
     * @param mimeTypes 允许的 MIME；为空不限
     * @param facts 文件事实
     * @return 问题说明；合法时为 null
     */
    public static String valueProblem(JsonNode value, Integer minFiles, Integer maxFiles, List<String> mimeTypes,
        Map<Long, AttachmentFact> facts) {
        List<Long> ids = fileIds(value);
        if (ids == null) {
            return "必须是文件地址数组";
        }
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            return "文件不能重复";
        }
        if (minFiles != null && ids.size() < minFiles) {
            return "至少需要 " + minFiles + " 个文件";
        }
        if (maxFiles != null && ids.size() > maxFiles) {
            return "最多 " + maxFiles + " 个文件";
        }
        List<String> problems = new ArrayList<>();
        ids.forEach(id -> fileProblem(id, facts.get(id), mimeTypes).ifPresent(problems::add));
        return problems.isEmpty() ? null : String.join("，", problems);
    }

    /**
     * 单个文件的内容问题。
     *
     * @param id 文件 ID
     * @param fact 文件事实；查不到为 null
     * @param mimeTypes 允许的 MIME；为空不限
     * @return 问题说明
     */
    static Optional<String> fileProblem(long id, AttachmentFact fact, List<String> mimeTypes) {
        if (fact == null) {
            return Optional.of("文件不存在：" + address(id));
        }
        if (fact.objectStatus() != FileObjectStatus.ACTIVE) {
            return Optional.of("文件不可用：" + address(id));
        }
        if (!mimeTypes.isEmpty() && !mimeTypes.contains(fact.mimeType())) {
            return Optional.of("文件类型 " + fact.mimeType() + " 不在允许范围：" + address(id));
        }
        if (fact.scanning()) {
            return Optional.of("文件扫描中，扫描通过后请重新验证：" + address(id));
        }
        if (!fact.available()) {
            return Optional.of("文件未通过安全扫描：" + address(id));
        }
        return Optional.empty();
    }
}
