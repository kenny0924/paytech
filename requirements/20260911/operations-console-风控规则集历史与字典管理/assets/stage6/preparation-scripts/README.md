# 阶段 6 准备脚本证据

这些是本轮在 `/tmp/rco16-stage6` 执行的脚本副本，保存检查逻辑，不是部署入口。依赖当前主工作区、JDK 25、原 test 工具和本轮临时文件。`preflight.py` / `final_checks.py` 只读 test；`compatibility.py` 从容器复制 JAR 后离线编译，原 Gateway 的 51 条完整快照会按证据失败；`CheckGatewayProposal.java` 只在内存验证待确认的路径根。

`preflight.py` 和 `prepare_sql.py` 已按最终权限时序更新：预检覆盖阶段 5 的批准修正；生成器必须提供实际角色/绑定快照，可显式重生成五份待审 SQL；`check_sql.py` 仅模拟状态/版本门禁，去掉 MySQL 专属会话/锁语法，不能替代真实 MySQL 验证。`build_images.py` 仅制作专用本地标签，依赖已经核对并打包的 JAR/dist。不要当作可直接重跑的发布脚本；不得用这些副本绕过阶段 7 授权。

2026-09-12 本次重新准备的入口和结果见 [完整候选发布包](../../../release-final.md)及 [stage6-final](../../stage6-final/)。其他旧构建/兼容脚本仍依赖旧临时目录，不得当作当前部署入口。
