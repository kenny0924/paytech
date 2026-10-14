-- Risk 通用 Client 入驻材料程序初始化 DML（mor_risk）。由 generate-risk-generic-materials.py 生成，勿手改。
-- 执行前必须取得精确执行授权；执行人先在同一会话 SET @approved_by=<真实 Operations 审批账号 ID>，未设置时 Risk 查询返回不可用。
-- 回滚：按下方 ID 依次 DELETE t_dd_program_requirement / t_dd_program_version / t_dd_program。
SET @now = UTC_TIMESTAMP(6);
START TRANSACTION;
INSERT INTO t_dd_program(program_id,program_code,qualification_type,status,create_time,update_time) VALUES(9100000000000001,'CLIENT_INDIVIDUAL_GENERIC','INDIVIDUAL_KYC','ACTIVE',@now,@now);
INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,review_frequency_code,status,active_slot,content_hash,approved_by,approved_time,create_time,update_time) VALUES(9100000000000101,9100000000000001,1,NULL,'ACTIVE',1,'d3cbeac0716a6c4247cf3e70afef169a86ae48a1f80bb7e255e3fa9005b4f925',@approved_by,@now,@now,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000001001,9100000000000101,'IDENTITY','IDENTITY_DOCUMENT',1,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000001002,9100000000000101,'ADDRESS','PROOF_OF_ADDRESS',0,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000001003,9100000000000101,'OTHER','SUPPORTING_DOCUMENT',0,@now);
INSERT INTO t_dd_program(program_id,program_code,qualification_type,status,create_time,update_time) VALUES(9100000000000002,'CLIENT_ORGANIZATION_GENERIC','ORGANIZATION_KYB','ACTIVE',@now,@now);
INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,review_frequency_code,status,active_slot,content_hash,approved_by,approved_time,create_time,update_time) VALUES(9100000000000102,9100000000000002,1,NULL,'ACTIVE',1,'2d6d23c3909f68d1f8b27a7585db28be6b3d7cfd90ff09fc29a733def2580446',@approved_by,@now,@now,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000002001,9100000000000102,'REGISTRATION','REGISTRATION_CERTIFICATE',1,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000002002,9100000000000102,'OWNERSHIP','OWNERSHIP_STRUCTURE',0,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000002003,9100000000000102,'AUTHORIZATION','AUTHORIZATION_LETTER',0,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000002004,9100000000000102,'ADDRESS','PROOF_OF_ADDRESS',0,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000002005,9100000000000102,'OTHER','SUPPORTING_DOCUMENT',0,@now);
INSERT INTO t_dd_program(program_id,program_code,qualification_type,status,create_time,update_time) VALUES(9100000000000003,'RELATED_PERSON_GENERIC','RELATED_PERSON_KYC','ACTIVE',@now,@now);
INSERT INTO t_dd_program_version(program_version_id,program_id,version_no,review_frequency_code,status,active_slot,content_hash,approved_by,approved_time,create_time,update_time) VALUES(9100000000000103,9100000000000003,1,NULL,'ACTIVE',1,'645fdb694555b7605dadf6be15ffddc84aee3af0160c12435410e40bb3ee584c',@approved_by,@now,@now,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000003001,9100000000000103,'IDENTITY','IDENTITY_DOCUMENT',1,@now);
INSERT INTO t_dd_program_requirement(program_requirement_id,program_version_id,requirement_code,document_type,required_flag,create_time) VALUES(9100000000003002,9100000000000103,'OTHER','SUPPORTING_DOCUMENT',0,@now);
COMMIT;
