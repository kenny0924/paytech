package com.paytech.operations.console.bff.service.model.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 草稿校验结果；不保存。 */
@Getter
@Setter
public class QueryTemplateValidationResponseBody {

    /** 是否没有 ERROR 级问题，即可以保存。 */
    private boolean valid;

    /** 问题列表，按发现顺序；没有问题时为空列表。 */
    private List<QueryTemplateProblem> problems;
}
