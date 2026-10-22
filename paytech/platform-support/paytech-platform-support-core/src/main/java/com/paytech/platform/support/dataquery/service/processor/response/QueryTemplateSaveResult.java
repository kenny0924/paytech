package com.paytech.platform.support.dataquery.service.processor.response;

import java.util.List;

import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.template.TemplateProblem;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板保存结果：前后事实与不阻止保存的警告。 */
@Getter
@AllArgsConstructor
public class QueryTemplateSaveResult {

    /** 前后事实。 */
    private final QueryTemplateChange change;

    /** 警告。 */
    private final List<TemplateProblem> warnings;
}
