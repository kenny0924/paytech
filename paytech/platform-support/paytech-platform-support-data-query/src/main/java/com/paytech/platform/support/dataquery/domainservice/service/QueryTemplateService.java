package com.paytech.platform.support.dataquery.domainservice.service;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.domain.ChangeQueryTemplateStatusCommand;
import com.paytech.platform.support.dataquery.domain.CreateQueryTemplateCommand;
import com.paytech.platform.support.dataquery.domain.ExecutableQueryTemplate;
import com.paytech.platform.support.dataquery.domain.QueryTemplateChange;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateSignature;
import com.paytech.platform.support.dataquery.domain.QueryTemplateSummary;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionDetail;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionSummary;
import com.paytech.platform.support.dataquery.domain.SaveQueryTemplateCommand;

/**
 * 查询模板与版本的本地查询与保存；内容合法性由调用方在事务前用保存校验确认。
 * <li>写方法加入调用方已有的本地事务；写入时对引用的数据源加共享锁并要求其为 ACTIVE，防止与停用、删除交错。</li>
 */
public interface QueryTemplateService {

    /** 分页查询模板，附当前版本的数据源、分页方式与参数声明。 */
    PagingResponseBody<QueryTemplateSummary> listQueryTemplates(Long dataSourceId, QueryTemplateStatus status,
        String keyword, PagingRequestBody paging);

    /**
     * 模板及当前版本。
     *
     * @param templateId 模板 ID
     * @return 事实；不存在时为 null
     */
    QueryTemplateDetail findQueryTemplate(long templateId);

    /**
     * 某个版本的内容。
     *
     * @param templateId 模板 ID
     * @param versionNo 版本号
     * @return 版本；不存在时为 null
     */
    QueryTemplateVersionDetail findQueryTemplateVersion(long templateId, int versionNo);

    /** 分页查询某模板的版本，按版本号倒序。 */
    PagingResponseBody<QueryTemplateVersionSummary> listQueryTemplateVersions(long templateId,
        PagingRequestBody paging);

    /**
     * 按编码加载执行所需的模板、当前版本与数据源。
     *
     * @param templateCode 模板编码
     * @return 事实；模板不存在时为 null
     */
    ExecutableQueryTemplate findExecutableTemplate(String templateCode);

    /**
     * 按编码读取模板签名：状态、当前版本数据源是否可用、参数与出参列声明。只读本库，可在事务内调用。
     *
     * @param templateCode 模板编码
     * @return 签名；模板不存在时为 null
     */
    QueryTemplateSignature findTemplateSignature(String templateCode);

    /** 新建模板，保存为版本 1，状态 ACTIVE。 */
    QueryTemplateChange createQueryTemplate(CreateQueryTemplateCommand command);

    /**
     * 按原版本保存为新版本并立即生效：插入版本行、切换当前版本与冗余数据源、模板版本加一。
     *
     * @param command 保存命令
     * @return 前后事实
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 版本不符或并发保存
     */
    QueryTemplateChange saveQueryTemplate(SaveQueryTemplateCommand command);

    /** 按原版本切换状态；目标与当前相同时不写入；启用要求当前版本数据源为 ACTIVE。 */
    QueryTemplateChange changeQueryTemplateStatus(ChangeQueryTemplateStatusCommand command);
}
