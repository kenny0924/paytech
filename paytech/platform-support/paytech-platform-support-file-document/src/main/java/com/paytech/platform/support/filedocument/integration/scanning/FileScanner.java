package com.paytech.platform.support.filedocument.integration.scanning;
import com.paytech.platform.support.filedocument.domain.file.FileModels.*;
/** 扫描器的供应商端口；生产未配置时不能自动通过。 */
public interface FileScanner {
    /** 无 I/O 的唯一受控适配器代码。 */
    String scannerCode();
    /** 对 fileId/requirementId/attemptNo 幂等执行或查询原检查；未知时抛出异常，不能返回伪造终局。必须读取准确版本并验证摘要。 */
    Result recoverOrScan(File file,Requirement requirement,int attemptNo);
    /** 按供应商真实版本规则比较，未知版本必须返回 false；不使用字符串字典序。 */
    boolean acceptsRuntime(String minimum,String actual);
}
