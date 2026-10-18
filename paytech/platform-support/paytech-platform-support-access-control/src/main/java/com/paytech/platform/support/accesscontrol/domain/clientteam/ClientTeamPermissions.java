package com.paytech.platform.support.accesscontrol.domain.clientteam;

import java.util.List;
import java.util.Map;

import com.paytech.platform.support.accesscontrol.domain.clientdeveloper.ClientDeveloperPermissions;
import com.paytech.platform.support.api.enums.ClientTeamRole;

/**
 * CLIENT 工作区团队权限码与四个系统角色的固定权限矩阵
 * <li>首次 ACTIVE 投影时按本矩阵创建四个角色及其权限；存量工作区由需求目录 DML 回填同一矩阵。</li>
 * <li>OWNER 与管理员权限相同，区别只在“不能修改或移除 OWNER、不能修改或移除自己”的保护规则。</li>
 */
public final class ClientTeamPermissions {

    /** 查看本工作区成员列表。 */
    public static final String TEAM_READ = "CLIENT_TEAM_READ";

    /** 查看邀请、发起 / 重发 / 撤销邀请、调整角色与移除成员。 */
    public static final String TEAM_MANAGE = "CLIENT_TEAM_MANAGE";

    private static final List<String> ALL = List.of(TEAM_READ, TEAM_MANAGE,
        ClientDeveloperPermissions.API_CREDENTIAL_READ, ClientDeveloperPermissions.API_CREDENTIAL_MANAGE,
        ClientDeveloperPermissions.WEBHOOK_READ, ClientDeveloperPermissions.WEBHOOK_MANAGE);

    private static final Map<ClientTeamRole, List<String>> MATRIX = Map.of(
        ClientTeamRole.TENANT_OWNER, ALL,
        ClientTeamRole.TENANT_ADMIN, ALL,
        ClientTeamRole.TENANT_DEVELOPER, List.of(TEAM_READ, ClientDeveloperPermissions.API_CREDENTIAL_READ,
            ClientDeveloperPermissions.API_CREDENTIAL_MANAGE, ClientDeveloperPermissions.WEBHOOK_READ,
            ClientDeveloperPermissions.WEBHOOK_MANAGE),
        ClientTeamRole.TENANT_VIEWER, List.of(TEAM_READ, ClientDeveloperPermissions.API_CREDENTIAL_READ,
            ClientDeveloperPermissions.WEBHOOK_READ));

    private ClientTeamPermissions() {}

    /** 系统角色的固定权限码。 */
    public static List<String> of(ClientTeamRole role) {
        return MATRIX.get(role);
    }
}
