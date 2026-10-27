package com.paytech.client.master.core.dal.mapper;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** t_application_declaration SQL；只追加。 */
public interface ApplicationDeclarationMapper {
    /** 追加一条声明。 */
    int insert(@Param("row") ApplicationDeclaration row);

    /** 申请的全部声明。 */
    List<ApplicationDeclaration> findByApplication(@Param("applicationId") long applicationId);
}
