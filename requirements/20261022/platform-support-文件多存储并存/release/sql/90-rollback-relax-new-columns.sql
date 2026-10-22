-- 文件多存储并存 · 镜像回滚配套（mor_platform；只在把 platform-support-service 换回 compose-rollback.yaml 的旧镜像时执行）。
-- 原因：01 第 4 段把 source_system / reason_code 改为 NOT NULL 且无默认值，旧 Platform 的 INSERT 不写这两列，严格模式下入驻开始上传 /
--   完成上传会失败。本文件只把四列放宽为可空（保留已有数据与 idx_file_source），旧 Platform 即可写入；旧代码不读这些列与 3 张存储表。
-- 顺序：先停用块 130（../test-deactivate.sql），再换回旧 Platform，随后立即执行本文件（窗口内入驻上传失败，旧文件读取不受影响）。
-- 再次前滚：换回新 Platform 前先回填旧 Platform 期间产生的空值，再恢复 NOT NULL（即 01 第 3、4 段，ADD KEY 已存在需去掉）：
--   UPDATE t_file_upload SET source_system='platform-support', reason_code='CLIENT_ONBOARDING_MATERIAL' WHERE source_system IS NULL;
--   UPDATE t_file_object SET source_system='platform-support', reason_code='CLIENT_ONBOARDING_MATERIAL' WHERE source_system IS NULL;
--   然后执行 01 第 4 段的 MODIFY（不含 ADD KEY）。
SET NAMES utf8mb4;

ALTER TABLE `t_file_upload`
  MODIFY COLUMN `source_system` VARCHAR(64) NULL COMMENT '上传来源系统，即开始上传时内部调用身份的服务名；Platform 自行发起的上传为 platform-support。创建后不变。取值来源：内部调用身份的服务名；非固定枚举。',
  MODIFY COLUMN `reason_code` VARCHAR(64) NULL COMMENT '来源系统声明的上传原因代码，格式 [A-Z][A-Z0-9_]{0,63}，用于反查文件为什么上传；创建后不变。取值来源：来源系统自定义；非固定枚举。';

ALTER TABLE `t_file_object`
  MODIFY COLUMN `source_system` VARCHAR(64) NULL COMMENT '文件来源系统，完成上传时从上传记录复制，用于反查文件来源。取值来源：内部调用身份的服务名；非固定枚举。',
  MODIFY COLUMN `reason_code` VARCHAR(64) NULL COMMENT '来源系统声明的上传原因代码，完成上传时从上传记录复制。取值来源：来源系统自定义；非固定枚举。';

SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('t_file_upload', 't_file_object') AND COLUMN_NAME IN ('source_system', 'reason_code')
 ORDER BY TABLE_NAME, COLUMN_NAME;
