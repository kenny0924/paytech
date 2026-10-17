package com.paytech.platform.support.filedocument.core.dal.dataobject;
import com.paytech.platform.support.filedocument.domain.enums.FileScanType;
/**
 * 冻结策略要求及已有结果的 Owner 内部投影。
 * @param id 原要求身份
 * @param scannerCode 受控扫描器
 * @param type 扫描类别
 * @param minimumVersion 最低运行版本
 * @param acceptedCodes 接受结果代码 JSON
 * @param resultCode 已追加的终局结果；缺失时为空
 */
public record FileScanRequirement(long id,String scannerCode,FileScanType type,String minimumVersion,String acceptedCodes,String resultCode) {}
