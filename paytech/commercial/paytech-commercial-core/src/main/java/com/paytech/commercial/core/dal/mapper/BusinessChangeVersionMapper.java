package com.paytech.commercial.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.BusinessChangeVersion;

/** t_business_change_version（commercial 实例）的持久化读写；快照只插入。 */
public interface BusinessChangeVersionMapper {
    /** 插入一条不可变快照。 */
    int insert(@Param("row") BusinessChangeVersion row);

    /** 读取一条历史身份的全部快照。 */
    List<BusinessChangeVersion> findByHistoryId(@Param("historyId") long historyId);
}
