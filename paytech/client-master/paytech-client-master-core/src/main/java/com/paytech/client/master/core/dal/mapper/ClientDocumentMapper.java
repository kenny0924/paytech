package com.paytech.client.master.core.dal.mapper;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.paytech.client.master.core.dal.dataobject.ClientApplicationDocument;
/** 仅访问 CCT 原申请材料与已授权主体资料。 */
public interface ClientDocumentMapper {
    /** 当前材料引用，不查询 Platform 文件状态。 */
    List<ClientApplicationDocument> documents(@Param("applicationId") long applicationId);
    /** 精确原引用只用于当前申请。 */
    ClientApplicationDocument document(@Param("applicationId") long applicationId,@Param("id") long id);
    /** 原申请锁内保存准确文件地址与摘要。 */
    void insert(@Param("applicationId") long applicationId,@Param("document") ClientApplicationDocument document,@Param("now") Instant now);
    /** 移除引用，绝不操作 Platform。 */
    int remove(@Param("applicationId") long applicationId,@Param("id") long id);
}
