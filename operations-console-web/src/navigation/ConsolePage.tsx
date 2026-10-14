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
  OPERATIONS_CONSOLE_RESOURCES: lazy(() => import('../pages/ConsoleResources').then(m => ({ default: m.ConsoleResources }))),
  OPERATIONS_API_CREDENTIALS: lazy(() => import('../pages/ApiCredentials').then(m => ({ default: m.ApiCredentials }))),
  OPERATIONS_TENANT_ACCESS: lazy(() => import('../pages/TenantAccess').then(m => ({ default: m.TenantAccess }))),
  OPERATIONS_API_SCOPES: lazy(() => import('../pages/ApiScopes').then(m => ({ default: m.ApiScopes }))),
  OPERATIONS_PERMISSIONS: lazy(() => import('../pages/Permissions').then(m => ({ default: m.Permissions }))),
  OPERATIONS_EXTERNAL_STANDARD_APIS: lazy(() => import('../pages/ExternalStandardApis').then(m => ({ default: m.ExternalStandardApis }))),
  OPERATIONS_EXTERNAL_INTEGRATIONS: lazy(() => import('../pages/ExternalIntegrations').then(m => ({ default: m.ExternalIntegrations }))),
  OPERATIONS_CLIENT_ONBOARDING_REVIEW: lazy(() => import('../pages/ClientOnboardingReview').then(m => ({ default: m.ClientOnboardingReview }))),
};
export function ConsolePage({ componentKey, ...props }: PageProps & { componentKey: string }) {
  const Page = pages[componentKey as PageComponentKey];
  return Page ? <Page {...props} /> : null;
}
