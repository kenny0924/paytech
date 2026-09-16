# Platform 入驻文件：本机七表执行范围

合同补充已由用户回复“可以”确认，见[文件合同](../../file-upload-closure-proposal.md)。用户随后再次回复“可以”，单独批准本文件列出的精确本机 SQL 动作及相关验证；授权不包括部署或生产文件服务启用。

- 目标：Docker `desktop-linux` / `mor-local-mysql-1`，`127.0.0.1:13306/mor_platform_config_validation`。
- 唯一 SQL：[platform-files-local.sql](./platform-files-local.sql)。SHA-256：`358960eae6650c7734eb940db5cbd35be85107dacbfd552e58d35383cae07791`。
- 动作：7 条 CREATE，表为 `t_file_upload`、`t_file_scan_policy`、`t_file_scan_requirement`、`t_file_object`、`t_file_owner_ref`、`t_file_scan_result`、`t_compensation_event`；共 91 列、7 个主键、13 个唯一索引、11 个普通索引。
- 业务初始化、扫描策略、真实扫描结果、Risk 材料规则及 Route/权限 DML：0 行。相关自动测试只创建并回收自身合成夹具，不清空现有表。
- 保留当前 26 张表结构和数据；不运行旧整份 Platform additions SQL，不修改其他数据库、账号授权、外部资源或应用开关。

## 执行前后核验

最新[只读前置证据](../../assets/stage4/platform-files-execution-preflight-20260915.json)证明七张候选表不存在，记录已有 26 张表的逐表计数和定义 Hash。[已有结构备份](../../assets/stage4/platform-files-existing-schema-before.sql)只用于审阅和恢复参考，不自动执行。

执行时重新检查容器、固定端口、数据库名、SQL 指纹和七表缺失状态；持有 Platform 本机目录测试隔离锁，若有并行写入或目标漂移则停止核对。凭据只从经核验运行容器读入内存，不读取 `.env.local`，不写日志或文件。每条 SQL 明确执行一次，应用启动和测试不建表。

执行后逐列比较类型、顺序、NULL、默认值、字符集/排序规则、注释、主键与索引；新增七表应为空，其余 26 表的定义和计数应与执行前相同。证据单独归档，后续测试使用真实本机 MySQL/Redis。

MySQL DDL 无整体事务回滚：任一 CREATE 失败即停止并记录已创建的精确表，不自动 DROP 或盲目重跑。如果需要撤回，仅在再次确认新增表为空、没有消费者且取得精确删除授权后处理；原 26 表无需恢复性重建。

## 授权边界

设计确认与 SQL 执行分别授权，根 [AGENTS.md 的 Change discipline](../../../../../AGENTS.md#change-discipline)要求“SQL 应用、提交、推送、创建分支或 PR、部署、发布及配置外部资源分别判断授权”。用户已单独确认上述指纹、七张表和目标库；SQL 文件保留原评审注释与指纹，不因授权状态变化重写内容。

执行证据见[逐表执行记录](../../assets/stage4/platform-files-execution-20260915.json)及[独立只读复核](../../assets/stage4/platform-files-post-execution-verification-20260915.json)。前者保留首次校验脚本对时间默认值大小写的误报，后者说明修正并重新读取元数据的依据；没有重跑 DDL。当前环境状态统一从[实现台账](../../../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)读取。
