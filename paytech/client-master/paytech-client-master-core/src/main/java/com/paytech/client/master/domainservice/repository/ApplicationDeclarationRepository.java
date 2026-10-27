package com.paytech.client.master.domainservice.repository;

import com.paytech.client.master.core.dal.dataobject.ApplicationDeclaration;
import java.util.List;

/** t_application_declaration 仓储；声明不可变、只追加。 */
public interface ApplicationDeclarationRepository {
    /**
     * 追加一条声明
     * 
     * @param row 已准备 ID 与时间的声明
     */
    void insert(ApplicationDeclaration row);

    /**
     * 申请的全部声明，按时间先后
     * 
     * @param applicationId 申请 ID
     * @return 声明
     */
    List<ApplicationDeclaration> findByApplication(long applicationId);
}
