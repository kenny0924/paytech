# RCO-17 综合验证：独立本机 Platform 注册与临时开放

状态：本次具体方案已获用户“可以”授权，注册、临时开放及收尾关闭均已执行；详见 [综合验证记录](../../../acceptance.md)。目标仅为既有 `127.0.0.1:13306/mor_platform_rco16_validation`（容器 `mor-local-mysql-1`）。不新建库、表、用户或授权，不修改原 `mor_platform`、test 环境。

## 已完成的只读核对

[precheck.sql](./precheck.sql) 与 [precheck-observed.tsv](./precheck-observed.tsv) 记录 2026-09-12 的结果：本次 118 行的 PK、Route code/method+path、Permission code、Resource code 均无冲突；Target、读权限、风控父菜单和管理员角色符合前置身份。该库账号、会话、签名密钥均为零；管理员角色版本为 3。执行前必须重查，状态不符时停止。

## 精确变更

1. 备份相关 8 张配置表到 `~/Code-MoR-backups/`（本次 7 张插入表和 `t_role`），记录前置版本。
2. 手动应用上一级已评审的 [dml.sql](../dml.sql)：插入 118 条，新增 Route/Resource 保持 DISABLED，两条写权限绑定保持 REVOKED，既有管理员角色版本 3→4。无 DDL。不复制或改写注册权威。
3. [open.sql](./open.sql) 仅临时开放 **12 Route、3 Resource、2 RolePermission**，另将管理员角色版本 4→5：10 条决策流接口，外加既有拒绝原因列表/详情 GET（012003、012004）；三个决策流资源、两条 FLOW_SAVE/FLOW_DELETE 绑定。未开放其他字典写入、旧 Candidate 或发布能力。
4. 通过隔离的本地测试数据验证数据库 Snapshot、Gateway 权限/转发以及 BFF/Risk；身份、会话和签名材料使用既有测试 Builder/Simulator，在进程或测试生命周期内生成并清理，不增加永久登录账号。报告必须区分 Simulator 与真实链路。
5. 验证后执行 [close.sql](./close.sql)：精确关闭上述 12 Route、3 Resource，撤销两个绑定，管理员角色版本 5→6。状态恢复到注册后状态；版本和修改时间正常递增，不倒退。保留 118 条关闭状态注册记录。清理仅本次拥有的业务测试数据。

open/close 不自行提交；在同一连接核对改动数依次为 12、3、2、1，再显式 COMMIT；任一不符则 ROLLBACK。注册 DML 的前置检查失败即停止，不重复执行。仅停止本次启动的验收进程；不停止其他任务服务。

本方案依据根 AGENTS.md 的手工 SQL 规则与已确认阶段边界，需要独立确认；不包含制品发布、test 部署或 Git 提交。

## 2026-09-12 执行结果

备份位于 `/Users/kenny/Code-MoR-backups/rco17-platform-stage5-20260912T150157Z`。118 插入及角色版本 3→4 核对通过；open/close 均在同连接检查 12/3/2/1 后提交，角色最终 version=6，118 条关闭状态注册保留。临时账号、会话、密钥、角色及业务测试事实已清理。Gateway 同进程确认 404→200→404。

原方案漏了一条已有规则历史 GET，仅影响本机规则选择器验收；[补充开放](./rule-history-open.sql)和[关闭](./rule-history-close.sql)尚待授权，仍为 DISABLED/version=1。公共校验错误转发也有已复现问题，阶段 5 尚未通过。将来重开原 12 条时应以当前 version=3、角色 version=6 准备准确 CAS SQL，不能原样重跑首次 open.sql；原脚本保留为本次已执行证据。


## 补充授权后的重验与关闭

用户回复“都同意”后，以实际版本执行 [recheck-open.sql](./recheck-open.sql) 与 [recheck-close.sql](./recheck-close.sql)，将原 12 条及补充历史 GET 合计 13 条临时开启并关闭；没有重跑 118 条插入，也没有单独执行早先 rule-history-open/close.sql。原独立补充脚本保留为提案证据，不可用于当前版本。

本轮备份 `/Users/kenny/Code-MoR-backups/rco17-platform-recheck-20260912T154835Z`；每次同连接核对 12/1/3/2/1 后显式提交。最终原 Route/Resource/RolePermission version=5，额外 GET DISABLED/version=3，角色 version=8；业务和临时身份事实已清理。准确结果及实际使用的本机限制见 [综合验证记录](../../../acceptance.md)。本期注册未应用到 test。
