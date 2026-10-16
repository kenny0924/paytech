package com.paytech.finance.accounting.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.CloseApproval;

/** t_close_approval：关账双人审批记录持久化（只追加）。 */
@Mapper
public interface CloseApprovalMapper {

    /** 插入审批记录。 */
    int insert(CloseApproval record);

    /** 读取流程的全部审批记录。 */
    List<CloseApproval> selectByClose(@Param("periodCloseId") long periodCloseId);
}
