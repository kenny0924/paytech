import type { Catalog } from './runtime.ts';
import commonNs from './en/common.json' with { type: 'json' };
import authNs from './en/auth.json' with { type: 'json' };
import navNs from './en/nav.json' with { type: 'json' };
import enumNs from './en/enum.json' with { type: 'json' };
import accessNs from './en/access.json' with { type: 'json' };
import gatewayNs from './en/gateway.json' with { type: 'json' };
import channelNs from './en/channel.json' with { type: 'json' };
import commercialNs from './en/commercial.json' with { type: 'json' };
import financeNs from './en/finance.json' with { type: 'json' };
import reconNs from './en/recon.json' with { type: 'json' };
import riskNs from './en/risk.json' with { type: 'json' };
import workflowNs from './en/workflow.json' with { type: 'json' };
import dataQueryNs from './en/data-query.json' with { type: 'json' };
import fileNs from './en/file.json' with { type: 'json' };
import notificationNs from './en/notification.json' with { type: 'json' };
import webhookNs from './en/webhook.json' with { type: 'json' };
import vaultNs from './en/vault.json' with { type: 'json' };
import cardNs from './en/card.json' with { type: 'json' };
import clientReviewNs from './en/client-review.json' with { type: 'json' };
import currencyNs from './en/currency.json' with { type: 'json' };
import i18nAdminNs from './en/i18n-admin.json' with { type: 'json' };
import qualificationNs from './en/qualification.json' with { type: 'json' };
import masterDataNs from './en/master-data.json' with { type: 'json' };

/** English base text bundled at build time; one JSON file per namespace (see namespaces.ts). */
export const builtin: Catalog = {
  common: commonNs,
  auth: authNs,
  nav: navNs,
  enum: enumNs,
  access: accessNs,
  gateway: gatewayNs,
  channel: channelNs,
  commercial: commercialNs,
  finance: financeNs,
  recon: reconNs,
  risk: riskNs,
  workflow: workflowNs,
  'data-query': dataQueryNs,
  file: fileNs,
  notification: notificationNs,
  webhook: webhookNs,
  vault: vaultNs,
  card: cardNs,
  'client-review': clientReviewNs,
  currency: currencyNs,
  'i18n-admin': i18nAdminNs,
  qualification: qualificationNs,
  'master-data': masterDataNs,
};
