-- 参考数据目录：回退行政区 local_name 到此前手工编写的阿拉伯文名称与原列注释。
-- 需求：requirements/20261027/platform-support-参考数据目录与client-master迁移（阶段 5 用户决定 E-2，2026-10-27）。
-- 目标库：Platform Support（mor_platform）。适用于已执行 changes/2026-10-27-reference-data.sql 的环境；新环境由该文件与
--   platform-support-initial-data.sql 直接得到相同结果，不需要本文件。由开发者审核后手工执行，应用不会执行本文件。
-- 回退对象：changes/2026-10-27-reference-data-subdivision-local-name.sql。
-- 内容：只改 t_ref_country_subdivision.local_name 的列注释（类型、可空、排序规则不变）与 47 行 local_name；实际改变的行 version 加一、更新 update_time；不改 status。
-- 幂等：重复执行为空操作（UPDATE 按编码设定固定值；注释同值）。执行前后各输出一次计数。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

SELECT 'before' AS `phase`, COUNT(*) AS `rows`, SUM(`local_name` IS NULL) AS `local_name_null` FROM `t_ref_country_subdivision`;
ALTER TABLE `t_ref_country_subdivision` MODIFY COLUMN `local_name` VARCHAR(128) NULL COMMENT '本地文字官方名称，如阿拉伯文；ISO 3166-2 未公布本地文字时取该国官方写法。用于地址打印与登记信息比对，不是界面翻译。没有时为空。';
START TRANSACTION;
UPDATE `t_ref_country_subdivision` SET `local_name` = 'عجمان', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'AE-AJ' AND NOT (`local_name` <=> 'عجمان');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'أبوظبي', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'AE-AZ' AND NOT (`local_name` <=> 'أبوظبي');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'دبي', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'AE-DU' AND NOT (`local_name` <=> 'دبي');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الفجيرة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'AE-FU' AND NOT (`local_name` <=> 'الفجيرة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'رأس الخيمة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'AE-RK' AND NOT (`local_name` <=> 'رأس الخيمة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الشارقة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'AE-SH' AND NOT (`local_name` <=> 'الشارقة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'أم القيوين', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'AE-UQ' AND NOT (`local_name` <=> 'أم القيوين');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الإسكندرية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-ALX' AND NOT (`local_name` <=> 'الإسكندرية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'أسوان', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-ASN' AND NOT (`local_name` <=> 'أسوان');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'أسيوط', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-AST' AND NOT (`local_name` <=> 'أسيوط');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'البحر الأحمر', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-BA' AND NOT (`local_name` <=> 'البحر الأحمر');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'البحيرة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-BH' AND NOT (`local_name` <=> 'البحيرة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'بني سويف', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-BNS' AND NOT (`local_name` <=> 'بني سويف');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'القاهرة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-C' AND NOT (`local_name` <=> 'القاهرة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الدقهلية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-DK' AND NOT (`local_name` <=> 'الدقهلية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'دمياط', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-DT' AND NOT (`local_name` <=> 'دمياط');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الفيوم', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-FYM' AND NOT (`local_name` <=> 'الفيوم');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الغربية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-GH' AND NOT (`local_name` <=> 'الغربية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الجيزة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-GZ' AND NOT (`local_name` <=> 'الجيزة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الإسماعيلية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-IS' AND NOT (`local_name` <=> 'الإسماعيلية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'جنوب سيناء', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-JS' AND NOT (`local_name` <=> 'جنوب سيناء');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'القليوبية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-KB' AND NOT (`local_name` <=> 'القليوبية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'كفر الشيخ', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-KFS' AND NOT (`local_name` <=> 'كفر الشيخ');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'قنا', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-KN' AND NOT (`local_name` <=> 'قنا');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الأقصر', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-LX' AND NOT (`local_name` <=> 'الأقصر');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'المنيا', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-MN' AND NOT (`local_name` <=> 'المنيا');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'المنوفية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-MNF' AND NOT (`local_name` <=> 'المنوفية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'مطروح', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-MT' AND NOT (`local_name` <=> 'مطروح');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'بورسعيد', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-PTS' AND NOT (`local_name` <=> 'بورسعيد');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'سوهاج', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-SHG' AND NOT (`local_name` <=> 'سوهاج');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الشرقية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-SHR' AND NOT (`local_name` <=> 'الشرقية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'شمال سيناء', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-SIN' AND NOT (`local_name` <=> 'شمال سيناء');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'السويس', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-SUZ' AND NOT (`local_name` <=> 'السويس');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الوادي الجديد', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'EG-WAD' AND NOT (`local_name` <=> 'الوادي الجديد');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الرياض', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-01' AND NOT (`local_name` <=> 'الرياض');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'مكة المكرمة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-02' AND NOT (`local_name` <=> 'مكة المكرمة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'المدينة المنورة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-03' AND NOT (`local_name` <=> 'المدينة المنورة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الشرقية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-04' AND NOT (`local_name` <=> 'الشرقية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'القصيم', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-05' AND NOT (`local_name` <=> 'القصيم');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'حائل', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-06' AND NOT (`local_name` <=> 'حائل');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'تبوك', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-07' AND NOT (`local_name` <=> 'تبوك');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الحدود الشمالية', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-08' AND NOT (`local_name` <=> 'الحدود الشمالية');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'جازان', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-09' AND NOT (`local_name` <=> 'جازان');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'نجران', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-10' AND NOT (`local_name` <=> 'نجران');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الباحة', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-11' AND NOT (`local_name` <=> 'الباحة');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'الجوف', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-12' AND NOT (`local_name` <=> 'الجوف');
UPDATE `t_ref_country_subdivision` SET `local_name` = 'عسير', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6) WHERE `subdivision_code` = 'SA-14' AND NOT (`local_name` <=> 'عسير');
COMMIT;
SELECT 'after' AS `phase`, COUNT(*) AS `rows`, SUM(`local_name` IS NULL) AS `local_name_null` FROM `t_ref_country_subdivision`;
