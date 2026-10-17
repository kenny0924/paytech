#!/usr/bin/env bash
set -euo pipefail

readonly REQUIRED_PASSWORD_VARIABLES=(
  MYSQL_ROOT_PASSWORD
  MOR_CCT_DB_PASSWORD
  MOR_COMMERCE_DB_PASSWORD
  MOR_PAYMENT_DB_PASSWORD
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
CREATE DATABASE IF NOT EXISTS mor_commerce CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_risk CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_finance CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS mor_vault CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS nacos_config CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS xxl_job CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS paytech_foundation_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'cct_app'@'%' IDENTIFIED BY '${MOR_CCT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'commerce_app'@'%' IDENTIFIED BY '${MOR_COMMERCE_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'payment_app'@'%' IDENTIFIED BY '${MOR_PAYMENT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'risk_app'@'%' IDENTIFIED BY '${MOR_RISK_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'finance_app'@'%' IDENTIFIED BY '${MOR_FINANCE_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'platform_app'@'%' IDENTIFIED BY '${MOR_PLATFORM_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'vault_app'@'%' IDENTIFIED BY '${MOR_VAULT_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'nacos'@'%' IDENTIFIED BY '${MOR_NACOS_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'xxl_job'@'%' IDENTIFIED BY '${MOR_XXL_JOB_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'foundation_test'@'%' IDENTIFIED BY '${PAYTECH_FOUNDATION_TEST_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'cdc_reader'@'%' IDENTIFIED BY '${MOR_CDC_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON mor_cct.* TO 'cct_app'@'%';
GRANT ALL PRIVILEGES ON mor_commerce.* TO 'commerce_app'@'%';
GRANT ALL PRIVILEGES ON mor_payment.* TO 'payment_app'@'%';
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
