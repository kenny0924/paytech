package com.paytech.risk.compliance.core.dal.mapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.risk.compliance.core.dal.dataobject.*;
/** 尽调材料仅访问 Risk 的三张既有目录表。 */
public interface MaterialRequirementsMapper {
    /** 至多取两个生效候选，以识别歧义而不挑第一条。 */
    List<MaterialProgram> programs(@Param("code") String code,@Param("qualificationType") String qualificationType);
    /** 只读取该准确修订材料；多取一条以识别上限。 */
    List<MaterialRequirement> requirements(@Param("versionId") long versionId);
}
