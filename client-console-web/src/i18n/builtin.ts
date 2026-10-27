import type { Catalog } from './runtime.ts';
import commonNs from './en/common.json' with { type: 'json' };
import authNs from './en/auth.json' with { type: 'json' };
import onboardingNs from './en/onboarding.json' with { type: 'json' };
import workspaceNs from './en/workspace.json' with { type: 'json' };
import teamNs from './en/team.json' with { type: 'json' };
import invitationNs from './en/invitation.json' with { type: 'json' };
import developerNs from './en/developer.json' with { type: 'json' };
import enumNs from './en/enum.json' with { type: 'json' };
import onboardingSchemaNs from './en/onboarding-schema.json' with { type: 'json' };

/** English base text bundled at build time; one JSON file per namespace. */
export const builtin: Catalog = {
  common: commonNs,
  auth: authNs,
  onboarding: onboardingNs,
  workspace: workspaceNs,
  team: teamNs,
  invitation: invitationNs,
  developer: developerNs,
  enum: enumNs,
  // 国家包文案：DML 的 label_key（onboardingSchema.*）经 schemaI18nKey 映射为本命名空间（Platform 命名空间码只允许小写与连字符）。
  'onboarding-schema': onboardingSchemaNs,
};
