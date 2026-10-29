package com.paytech.platform.support.dataquery.domain;

import java.util.List;

import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * data-query 对其他模块发布的模板签名：编码、状态、当前版本的参数声明与出参列声明，不含 SQL 正文与连接信息。
 * <li>供工作流在保存动作模板、保存草稿与发布时校验 DATA_QUERY 配置；只反映读取时刻的当前版本。</li>
 */
@Getter
@AllArgsConstructor
public final class QueryTemplateSignature {

    /** 模板编码。 */
    private final String templateCode;

    /** 模板名称。 */
    private final String templateName;

    /** 模板状态。 */
    private final QueryTemplateStatus status;

    /** 当前版本的数据源是否存在且为 ACTIVE。 */
    private final boolean dataSourceActive;

    /** 当前版本的参数声明，按声明顺序。 */
    private final List<QueryTemplateParameter> parameters;

    /** 当前版本的出参列声明（columns_json），按声明顺序；没有声明时为空列表。 */
    private final List<QueryTemplateColumn> columns;
}
