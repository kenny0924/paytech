package com.paytech.risk.control.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.control.core.dal.dataobject.RiskList;

/** 风险名单定义表映射。 */
public interface RiskListMapper {
    /** 插入名单。 */
    int insert(@Param("row") RiskList row);

    /** 按 ID 读取名单。 */
    RiskList selectById(@Param("listId") long listId);

    /** 按编码读取名单。 */
    RiskList selectByCode(@Param("listCode") String listCode);

    /** 按编码稳定排序分页读取名单。 */
    List<RiskList> selectPage(@Param("limit") int limit, @Param("offset") long offset);

    /** 统计名单数量。 */
    long count();
}
