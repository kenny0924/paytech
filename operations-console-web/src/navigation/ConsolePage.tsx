import { lazy } from 'react';
import type { ComponentType } from 'react';
import type { ConsoleResource } from '../api/types';
import type { PageComponentKey } from './pageCapabilities';
interface PageProps { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void; locationKey: string }
const AuditPage = lazy(() => import('../pages/Audit').then(m => ({ default: m.Audit })));
const Accounts = lazy(() => import('../pages/Accounts').then(m => ({ default: m.Accounts })));
const Roles = lazy(() => import('../pages/Roles').then(m => ({ default: m.Roles })));
const RiskDictionary = lazy(() => import('../pages/RiskDictionary').then(m => ({ default: m.RiskDictionary })));
const pages: Record<PageComponentKey, ComponentType<PageProps>> = {
  OPERATIONS_ACCOUNTS: Accounts,
  OPERATIONS_ROLES: props => <Roles resources={props.resources} onSaved={props.onAuthorizationChanged} />,
  OPERATIONS_AUDIT: () => <AuditPage />,
  OPERATIONS_GATEWAY_CONFIGURATION: lazy(() => import('../pages/GatewayConfiguration').then(m => ({ default: m.GatewayConfiguration }))),
  OPERATIONS_RISK_RULE_CONFIGURATION: lazy(() => import('../pages/RiskRuleConfiguration').then(m => ({ default: m.RiskRuleConfiguration }))),
  OPERATIONS_RISK_DECISION_FLOWS: lazy(() => import('../pages/RiskDecisionFlows').then(m => ({ default: m.RiskDecisionFlows }))),
  OPERATIONS_RISK_REJECTION_REASONS: props => <RiskDictionary key={props.locationKey} kind="rejection-reasons" {...props} />,
  OPERATIONS_RISK_SUBJECT_TAGS: props => <RiskDictionary key={props.locationKey} kind="subject-tags" {...props} />,
  OPERATIONS_RISK_VARIABLES: lazy(() => import('../pages/RiskVariables').then(m => ({ default: m.RiskVariables }))),
  OPERATIONS_RISK_LISTS: lazy(() => import('../pages/RiskLists').then(m => ({ default: m.RiskLists }))),
  OPERATIONS_RISK_EXECUTION_RELEASES: lazy(() => import('../pages/RiskExecutionReleases').then(m => ({ default: m.RiskExecutionReleases }))),
  OPERATIONS_RISK_EVALUATIONS: lazy(() => import('../pages/RiskEvaluations').then(m => ({ default: m.RiskEvaluations }))),
  OPERATIONS_CONSOLE_RESOURCES: lazy(() => import('../pages/ConsoleResources').then(m => ({ default: m.ConsoleResources }))),
  OPERATIONS_API_CREDENTIALS: lazy(() => import('../pages/ApiCredentials').then(m => ({ default: m.ApiCredentials }))),
  OPERATIONS_TENANT_ACCESS: lazy(() => import('../pages/TenantAccess').then(m => ({ default: m.TenantAccess }))),
  OPERATIONS_API_SCOPES: lazy(() => import('../pages/ApiScopes').then(m => ({ default: m.ApiScopes }))),
  OPERATIONS_PERMISSIONS: lazy(() => import('../pages/Permissions').then(m => ({ default: m.Permissions }))),
  OPERATIONS_EXTERNAL_STANDARD_APIS: lazy(() => import('../pages/ExternalStandardApis').then(m => ({ default: m.ExternalStandardApis }))),
  OPERATIONS_EXTERNAL_INTEGRATIONS: lazy(() => import('../pages/ExternalIntegrations').then(m => ({ default: m.ExternalIntegrations }))),
  OPERATIONS_CLIENT_ONBOARDING_REVIEW: lazy(() => import('../pages/ClientOnboardingReview').then(m => ({ default: m.ClientOnboardingReview }))),
  OPERATIONS_VAULT_DATA_TYPES: lazy(() => import('../pages/VaultDataTypes').then(m => ({ default: m.VaultDataTypes }))),
  OPERATIONS_VAULT_ACCESS_RULES: lazy(() => import('../pages/VaultAccessRules').then(m => ({ default: m.VaultAccessRules }))),
  OPERATIONS_VAULT_POLICY_CHANGES: lazy(() => import('../pages/VaultPolicyChanges').then(m => ({ default: m.VaultPolicyChanges }))),
  OPERATIONS_VAULT_MANUAL_PROTECT: lazy(() => import('../pages/VaultManualProtect').then(m => ({ default: m.VaultManualProtect }))),
  OPERATIONS_VAULT_MANUAL_REVEAL: lazy(() => import('../pages/VaultManualReveal').then(m => ({ default: m.VaultManualReveal }))),
  OPERATIONS_VAULT_MANUAL_EVENTS: lazy(() => import('../pages/VaultManualEvents').then(m => ({ default: m.VaultManualEvents }))),
  OPERATIONS_NOTIFICATION_TEMPLATES: lazy(() => import('../pages/NotificationTemplates').then(m => ({ default: m.NotificationTemplates }))),
  OPERATIONS_NOTIFICATION_PROVIDERS: lazy(() => import('../pages/NotificationProviders').then(m => ({ default: m.NotificationProviders }))),
  OPERATIONS_NOTIFICATION_RECORDS: lazy(() => import('../pages/NotificationRecords').then(m => ({ default: m.NotificationRecords }))),
};
export function ConsolePage({ componentKey, ...props }: PageProps & { componentKey: string }) {
  const Page = pages[componentKey as PageComponentKey];
  return Page ? <Page {...props} /> : null;
}
