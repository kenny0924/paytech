package com.paytech.platform.support.dataquery.domainservice.service;

import java.util.Collection;
import java.util.Map;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;
import com.paytech.platform.support.dataquery.domain.ChangeQueryKeyTypeStatusCommand;
import com.paytech.platform.support.dataquery.domain.CreateQueryKeyTypeCommand;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeChange;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeDetail;
import com.paytech.platform.support.dataquery.domain.QueryKeyTypeReorder;
import com.paytech.platform.support.dataquery.domain.ReorderQueryKeyTypesCommand;
import com.paytech.platform.support.dataquery.domain.UpdateQueryKeyTypeCommand;

/**
 * 联合查询键类型的本地查询与保存；正则合法性由调用方在事务前确认。
 * <li>写方法加入调用方已有的本地事务；修改与启停先对键类型行加排他锁，与模板保存、启用时的共享锁互斥。</li>
 * <li>“被引用”指 ACTIVE 模板当前版本的 joint_json 中行主体、键参数或任一列使用该键类型；引用检查以共享锁读取最新提交。</li>
 */
public interface QueryKeyTypeService {

    /** 分页查询键类型，按 sort_no、编码升序，每项附 ACTIVE 模板当前版本的引用数。 */
    PagingResponseBody<QueryKeyTypeDetail> listQueryKeyTypes(QueryKeyTypeStatus status, String keyword,
        PagingRequestBody paging);

    /**
     * 按主键查询。
     *
     * @param keyTypeId 键类型 ID
     * @return 事实；不存在时为 null
     */
    QueryKeyTypeDetail findQueryKeyType(long keyTypeId);

    /**
     * 按编码批量查询，供模板保存校验；不加锁。
     *
     * @param codes 编码
     * @return 编码到事实；不存在的编码不出现在结果中
     */
    Map<String, QueryKeyTypeDetail> findQueryKeyTypes(Collection<String> codes);

    /**
     * 新建键类型，状态 ACTIVE。
     *
     * @param command 新建命令
     * @return 写入结果
     * @throws com.paytech.foundation.common.error.BizCheckFailException 编码已存在
     */
    QueryKeyTypeChange createQueryKeyType(CreateQueryKeyTypeCommand command);

    /**
     * 按原版本修改；被 ACTIVE 模板引用时不能修改值类型。
     *
     * @param command 修改命令
     * @return 前后事实
     * @throws com.paytech.foundation.common.error.ConcurrentRequestException 版本不符
     * @throws com.paytech.foundation.common.error.BizCheckFailException 被引用时修改值类型，消息列出前 5 个模板编码
     */
    QueryKeyTypeChange updateQueryKeyType(UpdateQueryKeyTypeCommand command);

    /**
     * 按原版本启停；目标与当前相同时不写入；被 ACTIVE 模板引用时不能停用。
     *
     * @param command 启停命令
     * @return 前后事实
     */
    QueryKeyTypeChange changeQueryKeyTypeStatus(ChangeQueryKeyTypeStatusCommand command);

    /**
     * 整体重排：items 必须恰好覆盖全部键类型且不重复，任一版本不符整体回滚。
     *
     * @param command 重排命令
     * @return 重排前后
     */
    QueryKeyTypeReorder reorderQueryKeyTypes(ReorderQueryKeyTypesCommand command);
}
