# Client API 阶段 5 本机验收启动器

复用并按本期范围调整[前序整链路启动器](../../../../platform-support-菜单权限与API配置管理/assets/stage5/harness/README.md)。本期仍为生产 Web → 本机入口代理 → 真实 Gateway → 真实 BFF → 真实 Platform → 独立本机 MySQL/Redis；开发 Vault/签名适配器和 SimpleDiscovery 边界同前序。

只允许 `127.0.0.1:13306/mor_platform_config_validation`。使用已手工准备的完整目录，不创建 Schema 或初始化目录。临时开放本期 9 Route/4 Resource 和所需前期 18 Route/4 Resource，仅给三个合成账号的自有角色分配权限；原管理员 REVOKED 关联不变。准备三条合成凭证、三条目录及其授权用于真实查询/撤销，运行私有文件位于权限 0700 的 `/tmp/plt-client-api-stage5`，不归档凭证或 Token。

先启动 web.mjs，然后按 Platform → BFF → Gateway 使用 start.py 启动。脚本显式使用 JDK 25 和本机私有环境；Java classpath 取各应用当前 Surefire 报告。本期修改代码后需重新构建对应服务并停止旧进程再重启。

浏览器/API 新增 Scope 先从 `/fixture?SCOPE` 取得受清理追踪的编码；Route/Target 同前序。入口代理记录请求归属，响应和鉴权全由真实服务处理。结束按 Web/Gateway/BFF → Platform 顺序正常终止；Platform 等待审计后清理自有数据，恢复 27 Route/8 Resource 到初始 DISABLED。它不会删除数据库或修改 test。
