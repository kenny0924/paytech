-- 文件多存储并存第二段 · CCT 镜像回滚配套（mor_client_master；只在把 client-master-service 换回旧镜像前执行，CCT 第 2 步尚未执行）。
-- 原因：新 CCT 只写 file_address，新关联的材料 file_id 为空；旧 CCT 读写 file_id，遇到空值会出错。
-- 本文件只按地址补回 file_id（地址格式 /files/v1/{fileId}，第 11 个字符起为 ID），不删列、不动唯一键；第 1 步的结构保留。
SET time_zone = '+00:00';

UPDATE `t_application_document` SET `file_id` = CAST(SUBSTRING(`file_address`, 11) AS UNSIGNED) WHERE `file_id` IS NULL;

SELECT COUNT(*) AS file_id_null_must_be_0 FROM `t_application_document` WHERE `file_id` IS NULL;
SELECT COUNT(*) AS mismatched_must_be_0 FROM `t_application_document`
 WHERE `file_address` IS NOT NULL AND `file_address` <> CONCAT('/files/v1/', `file_id`);
