import { registerServiceModelContract } from "./commerce-service-model-contract.mjs";

// consumer (mor_consumer) model contract: t_customer split from commerce-service in step 03; the stored-card setting belongs to client-master (D42).
// The oracle below is frozen independently of the generator manifest; a manifest change must update it on purpose.
const ORACLE = String.raw`
[t_customer]
customer_id BIGINT NN
customer_no VARCHAR(64) NN
client_id BIGINT NN
consumer_reference VARCHAR(128) NN
active_reference VARCHAR(128) N
customer_status VARCHAR(16) NN
deleted_time DATETIME(6) N
version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_customer_no(customer_no)
UK uk_customer_active_reference(client_id,active_reference)

[t_compensation_event]
id BIGINT NN
event_key VARCHAR(128) NN
event_type VARCHAR(64) NN
sub_event_type VARCHAR(64) NN
payload_json JSON N
execute_status VARCHAR(64) NN
execute_count INT NN
next_execute_at DATETIME(6) NN
lock_owner VARCHAR(64) N
lock_until DATETIME(6) N
last_error_code VARCHAR(64) N
last_error_summary TEXT N
lock_version BIGINT NN
create_time DATETIME(6) NN
update_time DATETIME(6) NN
UK uk_compensation_event(event_key,event_type,sub_event_type)
IDX idx_compensation_ready(execute_status,next_execute_at,id)
IDX idx_compensation_lease(execute_status,lock_until,id)
`;

registerServiceModelContract({
  service: "consumer",
  label: "Consumer",
  database: "mor_consumer",
  account: "consumer_app",
  oracle: ORACLE,
  totals: {
    "tables": 2,
    "fields": 25,
    "uniqueIndexes": 3,
    "accessIndexes": 2,
    "relations": 0,
    "diagramGroups": 2
  },
  modelSha256: "3b666848eea2028443d20c8e0da79b5df956581ee00f0c22e60c21325236ca39",
  structuralSha256: "efbebd9b370dc4fc85f61d32d2c6a49b31b870fb6332a0a65e71a8fc5a7b1cae",
  diagrams: [
    "Customer",
    "可靠执行"
  ],
  versionTables: [
    "t_customer"
  ],
  forbiddenTables: [
    "t_command_idempotency",
    "t_client_stored_card_setting",
    "t_commerce_order"
  ],
  forbiddenFields: [],
  relations: [],
});
