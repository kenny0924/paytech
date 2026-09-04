# Operations 自定义角色与权限配置

用户已确认仅管理 Operations 内部运营人员的角色与权限，并授权实现。

## 范围与边界

- 复用现有 `t_role` / `t_permission` / `t_role_permission` / Member Role，不新增表或字段。
- 自定义角色固定为当前 INTERNAL Tenant Scope，系统角色只读，角色编码创建后不可修改；不涉及 CLIENT/PARTNER 或角色启停。
- 编码、名称、权限及操作原因是本轮输入；现有模型没有说明字段，不引入不落库的假字段。
- 创建与更新原子保存角色和权限，使用既有幂等键、乐观锁版本及本地审计。允许空权限集合；完整旧授权与新授权都必须处于操作者可授予范围。
- 人员创建/角色分配校验所授角色的当前权限不得超过操作者；继续保护最后管理员和现有账号/租户约束。
- Platform 决定权限范围、事务和授权；BFF 聚合角色详情、可授权 Permission 和 Console Resource 为页面权限树；前端只展示并提交明确勾选的 Permission IDs。
- 树节点与权限不是一对一：相同 Permission 的多个资源同步勾选；无资源绑定的权限保留独立入口，目录自身权限与子权限分别选择。

## HTTP 契约

公共路径在下表基础上加 `/api/operations`，四条新 Route 均绑定 `OPERATIONS_ROLE_MANAGE`：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/iam/v1/role-permission-options` | 创建角色时的授权选项树 |
| GET | `/iam/v1/roles/{roleId}` | 当前可编辑角色和权限树 |
| POST | `/iam/v1/roles` | 创建角色和权限 |
| PUT | `/iam/v1/roles/{roleId}` | 按 expectedVersion 更新名称和权限 |

旧角色分页接口仍使用 `OPERATIONS_ROLE_READ`。两个新按钮 `OPERATIONS_ROLE_CREATE` / `OPERATIONS_ROLE_EDIT` 共用管理权限。初始授权只增加到已有系统 `PLATFORM_ADMIN`；普通只读角色不获得管理能力。

Platform 内部 `OperationsManagementApi` 发布扁平 Role/Permission/Resource DTO，均集中于 `ManagementModels`；外部请求与响应继续使用 `RequestBody` / `ResponseBody` 后缀。

## 验证与发布

- Java：真实边界与业务规则测试，校验越权、系统角色、重复命令、并发版本及审计；BFF 验证聚合、错误传播、入站授权与 JSON 契约。
- 前端：权限联动、取消、失败保留输入、幂等提交及公共分页/错误通知回归；构建含严格类型检查。
- `platform-support-initial-data.sql` 保持唯一服务初始化 DML；既有 test 使用独立一次性增量 `test-environment/sql/2026-09-04-operations-role-management.sql`，不得重跑完整初始化或 Bootstrap。
- 不将自动数据库测试指向 test。test 发布与真实 HTTP 烟测单独记录；保留账号、密钥、基础设施及 Vault。

当前状态：实现并部署到 `http://localhost:28080`，真实 HTTP 与审计核验通过。

- Platform 全部测试源码编译通过，47 项纯规则/配置/架构/目录测试通过，最终 Web `-DskipTests install` 成功。
  `OperationsAuthenticationHttpAutoTest` 共 7 项真实 HTTP/MySQL 用例已编译，其中本轮新增 3 项；
  当前没有提供 `PLATFORM_SUPPORT_TEST_DB_PASSWORD` / `MOR_PLATFORM_DB_PASSWORD`，因此没有执行本地数据库 auto 套件，
  没有把这些测试重定向到 `mor-test`，不声明完整 JaCoCo 门槛通过。
- BFF JDK 25 `clean verify`：182 项全通过，包含授权拒绝、树聚合、异常与请求体边界。
- 前端 `npm test`：58 项全通过；`npm run build` 含严格 TypeScript 检查通过。
- 一次性增量实际应用后为 17 Route / 9 Resource；只更新 Platform、BFF、前端镜像，Gateway 动态消费目录。
  10 个现有 test 容器均为 running/healthy，`/roles` HTML 与入口静态资源逐字匹配本次构建。
- `roles_smoke.py` 经 Gateway→BFF→Platform→MySQL 验证创建/编辑、同键重放与冲突、版本冲突、系统角色保护、
  人员分配、菜单与接口权限、原 JWT 即时撤权/重新授权。受限管理员的目录严格收敛到自身权限，
  超范围创建角色、给自己授予 PLATFORM_ADMIN、读取更高权限角色配置均被拒绝。
- 另行只读核验：同一次创建重放只产生 1 条成功创建审计；6 次成功更新各有完整名称、版本、权限前后属性；
  所有 value Hash 和按 attr_no 组成的 Attribute Root Hash 均重新计算匹配。
  验收人员已停用，合成角色权限已清空，会话已退出，业务与审计数据保留。
- 原 `smoke.py` 全部通过：JSON 登录、Context、层级导航、三种分页/五档数量、业务/参数错误、Refresh、Logout、
  撤销后的 Refresh 拒绝及无 Cookie 响应。

浏览器验证使用独立临时 fixture 驱动真实 App，并在隔离的 headless Chrome 中运行，不读取用户浏览器会话。
创建/编辑抽屉、系统角色只读、同权限多节点同步、目录独立选择、纯分组禁选均通过；
POST 使用去重权限与幂等键，PUT 携带当前版本，每次保存只触发一次导航和角色列表重载。
桌面及 390px 移动端截图已核对，字段和抽屉未溢出。真实业务 HTTP 链路另由 mor-test 烟测验证。

## 后续用户纠正：操作独立与父子联动

本节覆盖此前共享 MANAGE 权限和父子独立交互。用户明确要求每个操作可独立勾选、选择父级应选中下级。
新增五个实际业务权限：ACCOUNT_CREATE/ACCOUNT_DISABLE/ACCOUNT_ASSIGN_ROLES/ROLE_CREATE/ROLE_UPDATE，
均带 OPERATIONS_ 前缀，固定初始化 ID 526–530；对应五个按钮和七条路由精确绑定，内部业务方法同样独立检查。
创建选项与创建 Route 使用 ROLE_CREATE，详情与修改使用 ROLE_UPDATE；BFF 内部共享目录允许两者任一。
创建者幂等重放不因没有 UPDATE 而失败，但继续检查角色范围和当前完整权限上限。

前端采用受控父子状态：选择节点包含子树与必要祖先，取消只移除该子树，部分选择显示半选；加载和重存不补授权。
旧管理权限通过一次性 `2026-09-04-operations-action-permissions.sql` 在同一事务中等价展开既有角色授权，
推进角色版本、撤销旧 Grants、旧 Permission 变 INACTIVE、重绑 Route/Resource；不新增表或改变 JWT。
新库唯一初始化 DML 直接包含9个权限，并修复角色 Route/Headers/Guards 的外键插入顺序。

Platform 47项纯测试及9个真实HTTP用例源码编译通过，数据库auto仍缺本地凭据未执行；
最后初始目录/外键顺序7项聚焦测试通过。BFF189项、前端62项及构建通过，实际App headless交互已核验。

操作权限修复已部署test。一次性迁移前后逐角色精确比较证明能力等价；独立操作烟测实际执行五种允许动作、
逐一拒绝其余四种，验证create-only重放和update-only详情。验收人员停用、角色权限清空，10容器健康，页面资源匹配新构建。

## 后续需求：删除角色与关联员工数

用户要求角色列表显示关联员工数，并支持角色删除。本节覆盖前述缺少删除操作的版本。

- Platform 批量统计当前 INTERNAL Tenant 中的 Operations HUMAN 员工角色关联；成员 ACTIVE/SUSPENDED、关联 ACTIVE 且未到期，
  包含停用账号及未来生效关联，以 Account ID 去重。每批最多200个角色，拒绝重复或越界ID，零关联返回0。
- BFF `RoleListProcessor` 聚合当前分页和单次批量计数，输出 `RoleListItem(version, employeeCount)`；人员内嵌角色字段保持。
- 删除使用独立 `OPERATIONS_ROLE_DELETE` Permission 531、Button 559、Route 1014，初始只授予系统管理员。
  `DELETE /iam/v1/roles/{roleId}` 接收 `expectedVersion`、必填原因和幂等键，成功204。
- 系统角色、有关联员工的角色不可删除；先在人员管理调整分配。服务端与角色分配共用 Account→Tenant 锁，
  锁内校验当前权限、目标完整权限上限、人数及版本。同事务角色置 INACTIVE、version+1、撤权、记录删除审计和成功幂等结果。
  重放不要求已删除角色仍 ACTIVE；历史角色/关联/审计保留，角色编码不再复用。
- 前端增加计数列与确认弹窗；等待时防重复，失败保留输入，同体重试同键；409需刷新，末页删除最后一条回上一页。
- 本次无新表/字段；新库唯一初始化 DML 为18 Route/10 Resource/10 Permission，既有 test 只执行
  `test-environment/sql/2026-09-04-operations-role-delete.sql`，不重放初始化或 Bootstrap。

本轮 Platform 48项纯测试通过，11个真实HTTP/MySQL测试源码已编译但因本地凭据缺失未运行；
BFF214项、前端64项及构建通过，实际App/headless交互验证通过。mor-test部署与真实HTTP验收单独记录。

本轮已更新 mor-test 的 Platform、BFF、前端镜像并显式应用删除目录增量；10个容器健康，页面与角色模块匹配新构建。
`role_deletion_smoke.py` 经真实 Gateway→BFF→Platform→MySQL 验证0/1关联人数、停用员工计数、关联转移、
独立删除权限、系统/关联角色保护、版本冲突、删除幂等重试、已删除角色不可分配及完整状态/版本/权限审计。
合成员工已停用并转到只读角色，合成角色经DELETE删除，验证会话退出；既有 `smoke.py` 回归通过。
