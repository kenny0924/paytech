import { TABLES as ACCOUNTING_FOUNDATION_TABLES } from "./manifests/01-accounting-foundation.mjs";
import { TABLES as TAX_ECONOMICS_COST_TABLES } from "./manifests/02-tax-economics-cost.mjs";
import { TABLES as ACCOUNTING_JOURNAL_TABLES } from "./manifests/03-accounting-journal.mjs";
import { TABLES as PROVIDER_CASH_RECON_TABLES } from "./manifests/05-provider-cash-recon.mjs";
import { TABLES as PAYOUT_TABLES } from "./manifests/06-payout.mjs";
import { TABLES as CLOSE_TABLES } from "./manifests/07-close.mjs";
import { TABLES as RELIABILITY_TABLES } from "./manifests/08-reliability.mjs";

export const TABLES = Object.freeze([
  ...ACCOUNTING_FOUNDATION_TABLES,
  ...TAX_ECONOMICS_COST_TABLES,
  ...ACCOUNTING_JOURNAL_TABLES,
  ...PROVIDER_CASH_RECON_TABLES,
  ...PAYOUT_TABLES,
  ...CLOSE_TABLES,
  ...RELIABILITY_TABLES,
]);

export const RELATIONS = Object.freeze(TABLES.flatMap((table) => table.relations));
