package com.paytech.operations.console.bff.service.model.dataquery.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 联合查询执行一层：本层键值、可选的模板范围与已加载对象；循环状态（visited、已加载对象、跳数、累计执行数、总时长）由页面保存。 */
@Getter
@Setter
public class JointQueryStepRequestBody {

    /**
     * 层号
     * <li>0..maxDepth-1，超出返回 422。只用于执行顺序与节点详情“第 n 跳”，不影响链路图定位。</li>
     */
    @NotNull
    @Min(0)
    private Integer depth;

    /**
     * 本层键
     * <li>depth=0：恰好 1 项，键类型须 ACTIVE 且 entryAllowed；值 1..maxEntryValues，逐个按值类型与 valuePattern 校验，
     * 不合法返回 422，field 形如 keys[0].values[3]。</li>
     * <li>depth&gt;0：1..20 项，同一键类型不重复，每项值 1..maxValuesPerKey；不做正则校验；键类型须 ACTIVE。</li>
     * <li>未知或停用键类型返回 422。</li>
     */
    @NotEmpty
    @Size(max = 20)
    private List<@Valid @NotNull JointQueryKeyValues> keys;

    /**
     * 只执行这些模板
     * <li>可选，最多 20 个模板编码，供页面“重试这一项”；不传或为空执行本层全部参与模板。不参与或不存在的编码忽略。</li>
     */
    @Size(max = 20)
    private List<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "templateCodes 的每一项只能为大写字母开头的大写字母、数字与下划线，2..64 位") String> templateCodes;

    /**
     * 已加载对象
     * <li>可选，最多 30 个键类型；页面从每个 SUCCEEDED 节点结果中、键类型等于该节点行主体键类型的列取非空值。</li>
     * <li>depth&gt;0 且未传 templateCodes 时，“按自身 ID 查”的模板（触发键类型 = 行主体键类型）剔除这些值：全部剔除则不执行、
     * 不生成节点、不计入单步执行数与预算，记入响应 alreadyLoaded；部分剔除则只用剩余值执行。其他模板不受影响。</li>
     * <li>未知或停用的键类型忽略；值按键类型规范化，不合法返回 422，field 形如 loadedSubjects[0].values[3]。</li>
     */
    @Size(max = 30)
    private List<@Valid @NotNull JointQueryLoadedSubject> loadedSubjects;
}
