#!/usr/bin/env bash
set -euo pipefail

readonly REQUIRED_PASSWORD_VARIABLES=(
  MYSQL_ROOT_PASSWORD
  MOR_CCT_DB_PASSWORD
  MOR_CLIENT_MASTER_DB_PASSWORD
  MOR_COMMERCIAL_DB_PASSWORD
  MOR_COMMERCE_DB_PASSWORD
  MOR_PAYMENT_DB_PASSWORD
  MOR_CHANNEL_CONNECTOR_DB_PASSWORD
  MOR_RISK_DB_PASSWORD
  MOR_FINANCE_DB_PASSWORD
  MOR_PLATFORM_DB_PASSWORD
  MOR_VAULT_DB_PASSWORD
  MOR_NACOS_DB_PASSWORD
  MOR_XXL_JOB_DB_PASSWORD
  PAYTECH_FOUNDATION_TEST_DB_PASSWORD
  MOR_CDC_DB_PASSWORD
)

validate_password() {
  local variable_name="$1"
  local password="${!variable_name:-}"
  if [[ ! "$password" =~ ^[A-Za-z0-9._@%+=:-]{16,128}$ ]]; then
    echo "Invalid local password format for ${variable_name}" >&2
    exit 1
  fi
}

for variable_name in "${REQUIRED_PASSWORD_VARIABLES[@]}"; do
  validate_password "$variable_name"
done

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
CREATE DATABASE IF NOT EXISTS mor_cct CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_client_master CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_commercial CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_commerce CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_channel_connector CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_risk CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_finance CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_vault CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS nacos_config CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS xxl_job CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS paytech_foundation_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'cct_app'@'%' IDENTIFIED BY '${MOR_CCT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'client_master_app'@'%' IDENTIFIED BY '${MOR_CLIENT_MASTER_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'commercial_app'@'%' IDENTIFIED BY '${MOR_COMMERCIAL_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'commerce_app'@'%' IDENTIFIED BY '${MOR_COMMERCE_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'payment_app'@'%' IDENTIFIED BY '${MOR_PAYMENT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'channel_connector_app'@'%' IDENTIFIED BY '${MOR_CHANNEL_CONNECTOR_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'risk_app'@'%' IDENTIFIED BY '${MOR_RISK_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'finance_app'@'%' IDENTIFIED BY '${MOR_FINANCE_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'platform_app'@'%' IDENTIFIED BY '${MOR_PLATFORM_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'vault_app'@'%' IDENTIFIED BY '${MOR_VAULT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'nacos'@'%' IDENTIFIED BY '${MOR_NACOS_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'xxl_job'@'%' IDENTIFIED BY '${MOR_XXL_JOB_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'foundation_test'@'%' IDENTIFIED BY '${PAYTECH_FOUNDATION_TEST_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'cdc_reader'@'%' IDENTIFIED BY '${MOR_CDC_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON mor_cct.* TO 'cct_app'@'%';
GRANT ALL PRIVILEGES ON mor_client_master.* TO 'client_master_app'@'%';
GRANT ALL PRIVILEGES ON mor_commercial.* TO 'commercial_app'@'%';
GRANT ALL PRIVILEGES ON mor_commerce.* TO 'commerce_app'@'%';
GRANT ALL PRIVILEGES ON mor_payment.* TO 'payment_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_channel_connector.* TO 'channel_connector_app'@'%';
GRANT ALL PRIVILEGES ON mor_risk.* TO 'risk_app'@'%';
GRANT ALL PRIVILEGES ON mor_finance.* TO 'finance_app'@'%';
GRANT ALL PRIVILEGES ON mor_platform.* TO 'platform_app'@'%';
GRANT ALL PRIVILEGES ON mor_vault.* TO 'vault_app'@'%';
GRANT ALL PRIVILEGES ON nacos_config.* TO 'nacos'@'%';
GRANT ALL PRIVILEGES ON xxl_job.* TO 'xxl_job'@'%';
GRANT ALL PRIVILEGES ON paytech_foundation_test.* TO 'foundation_test'@'%';

-- Flink CDC reader for the analytics profile: binlog replication globally, SELECT only on whitelisted databases
-- (paytech/data-service/cdc/whitelist.yaml). Add a GRANT SELECT line here when a new database enters the whitelist.
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'cdc_reader'@'%';
GRANT SELECT ON mor_finance.* TO 'cdc_reader'@'%';
EOSQL

# V2 Finance successors (refactor step 07, design/10 §5.1). Optional until compose.yaml passes the variables to the
# mysql container: each database/account is created only when its password variable is present, so an existing
# initialization keeps working. mor_finance and finance_app stay for the main branch; mor_account arrives in step 11b.
# account_app shares mor_ledger read-only in step 07.
create_v2_finance_database() {
  local database="$1" user="$2" variable_name="$3" privileges="$4" grant_database="$5"
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Skipping ${user}: ${variable_name} is not set" >&2
    return 0
  fi
  validate_password "$variable_name"
  mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
${database:+CREATE DATABASE IF NOT EXISTS ${database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;}
CREATE USER IF NOT EXISTS '${user}'@'%' IDENTIFIED BY '${!variable_name}';
GRANT ${privileges} ON ${grant_database}.* TO '${user}'@'%';
EOSQL
}

create_v2_finance_database mor_ledger ledger_app MOR_LEDGER_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_ledger
create_v2_finance_database mor_billing billing_app MOR_BILLING_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_billing
create_v2_finance_database "" account_app MOR_ACCOUNT_DB_PASSWORD "SELECT" mor_ledger
create_v2_finance_database mor_reconciliation reconciliation_app MOR_RECONCILIATION_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_reconciliation
create_v2_finance_database mor_tax tax_app MOR_TAX_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_tax

# V2 Commerce successors (refactor step 03, design/10 §5.1): same optional rule as above until compose.yaml passes the
# variables to the mysql container. mor_commerce and commerce_app stay for the main branch.
create_v2_finance_database mor_acquiring acquiring_app MOR_ACQUIRING_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_acquiring
create_v2_finance_database mor_consumer consumer_app MOR_CONSUMER_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_consumer

# V2 Risk successors (refactor step 06, design/10 §5.1): same optional rule. mor_risk and risk_app stay for the main branch;
# mor_dispute only owns Schema in step 06 and is not created.
create_v2_finance_database mor_risk_decision risk_decision_app MOR_RISK_DECISION_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_risk_decision
create_v2_finance_database mor_compliance compliance_app MOR_COMPLIANCE_DB_PASSWORD "SELECT, INSERT, UPDATE, DELETE" mor_compliance
