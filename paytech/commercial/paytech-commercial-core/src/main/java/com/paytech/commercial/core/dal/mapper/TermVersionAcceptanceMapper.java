package com.paytech.commercial.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commercial.core.dal.dataobject.TermVersionAcceptance;

/** t_term_version_acceptance 的持久化访问。 */
public interface TermVersionAcceptanceMapper {
    /** 插入一条签署记录，返回影响行数。 */
    int insert(TermVersionAcceptance acceptance);

    /** 按签署编号读取，不存在返回 null。 */
    TermVersionAcceptance selectByReference(@Param("acceptanceReference") String acceptanceReference);

    /** 读取一组版本的签署记录，按 term_version_id 升序；termVersionIds 必须非空。 */
    List<TermVersionAcceptance> selectByVersionIds(@Param("termVersionIds") Collection<Long> termVersionIds);
}
