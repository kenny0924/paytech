package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.*;
import com.paytech.risk.decision.domain.variable.RiskVariable.Query;
import java.util.List;
import java.time.Instant;
import com.paytech.risk.decision.domain.variable.VariableChange;
import org.apache.ibatis.annotations.Param;

/** 变量当前事实及成员的 SQL 入口，SQL 统一维护于 Mapper XML。 */
public interface RiskVariableMapper {
    /** 历史对照只投影系统映射，最多 100 项，不能取代变量候选目录。 */
    List<RiskVariableDefinition> systemDefinitions(@Param("pointCode") String pointCode);
    /** 读取变量，写锁必须是删除事务的首次数据库操作。 */
    RiskVariableDefinition find(@Param("code") String code, @Param("lock") String lock);
    /** 根据相同筛选条件稳定分页或按编码批取。 */
    List<RiskVariableDefinition> list(@Param("query") Query query, @Param("limit") int limit, @Param("offset") int offset);
    /** 统计普通查询的同范围总数。 */
    long count(@Param("query") Query query);
    /** 插入永久唯一的变量身份。 */
    int insertVariable(RiskVariableDefinition value);
    /** 按根版本更新允许的当前字段。 */
    int update(@Param("root") RiskVariableDefinition root, @Param("change") VariableChange change,
        @Param("actorId") long actorId, @Param("time") Instant time);
    /** 分页读取当前成员，排序由根的整批写入确定。 */
    List<RiskVariableMember> members(@Param("code") String code, @Param("pattern") String pattern,
        @Param("limit") int limit, @Param("offset") int offset, @Param("lock") boolean lock);
    /** 统计同筛选条件成员数。 */
    long memberCount(@Param("code") String code, @Param("pattern") String pattern);
    /** 批量读取每个集合的有限预览，不为列表逐项查询。 */
    List<RiskVariableMember> previewMembers(@Param("codes") List<String> codes, @Param("limit") int limit);
    /** 批量读取集合成员数。 */
    List<VariableMemberCount> memberCounts(@Param("codes") List<String> codes);
    /** 同一根事务内清除旧成员，随失败整体回滚。 */
    int deleteMembers(@Param("code") String code);
    /** 插入已规范化的唯一成员。 */
    int insertMember(RiskVariableMember value);
}
