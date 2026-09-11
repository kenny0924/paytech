#!/usr/bin/env python3
"""Explicit mor-test V1 configuration acceptance; creates immutable synthetic rules only."""
import argparse
import copy
import sys
import uuid

from initialize import mysql
from risk_rule_configuration_smoke import RiskRuleConfigurationSmoke, RiskRouteProbe, SmokeFailure

SAVE = "/decision-rule-sets/v1/rule-sets"


def require(value, code):
    if not value:
        raise SmokeFailure(code)


def parameter(code, value_type, value, sequence=1):
    return dict(parameterCode=code, valueSequence=sequence, sourceType="LITERAL",
                fieldCode=None, valueType=value_type, literalValue=value)


def runtime_counts():
    # Read each fact from its Owner database; configuration writes must not execute actions.
    tables = ("t_decision_evaluation", "t_evaluation_action_intent", "t_action_effect", "t_risk_restriction", "t_compensation_event")
    return tuple(mysql("SELECT COUNT(*) FROM `" + table + "`;", database="mor_risk") for table in tables)


def verify(base_url):
    runner = RiskRuleConfigurationSmoke(base_url)
    administrator = runner.authorization.administrator()
    actors = None
    try:
        observation = runner._observe(administrator)
        require(observation.stage == "RESOURCES_EXPOSED", "CONFIGURATION_STAGE_NOT_READY")
        actors = runner.authorization.discover(administrator, observation.stage)
        browser, denied = actors.authorized.browser, actors.denied.browser
        context = browser.request("GET", "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE")
        targets = context.get("targetBindings", [])
        require(len(targets) == 1 and targets[0]["bindingCode"] == "CURRENT_CLIENT_V1", "CONFIGURATION_BINDING_INVALID")
        require(targets[0]["requiredFields"] == [{"role": "SUBJECT_ID", "fieldCode": "client_id"}], "CONFIGURATION_BINDING_FIELDS_INVALID")
        require(len(context["actionParameters"]) == 4, "CONFIGURATION_PARAMETER_CATALOG_INVALID")
        extras = [action for action in context["actions"] if not action["terminalFlag"]]
        require({action["actionCode"] for action in extras} == {"APPLY_COOLDOWN", "ADD_RISK_TAG"}, "CONFIGURATION_ACTION_CATALOG_INVALID")
        require(all(action["status"] == "CONFIGURATION_ONLY" for action in extras), "CONFIGURATION_STATUS_INVALID")
        require(all(not ({"handlerCode", "implementationVersion", "implementationHash"} & set(action)) for action in context["actions"]), "HANDLER_EXPOSED")
        before = runtime_counts()
        probe = runner.route_probe
        request = probe._minimal_save_request(probe.rule_set_code_factory())
        rule = request["rules"][0]
        rule["action"] = None
        rule["additionalActions"] = [
            dict(sequenceNo=1, actionCode="APPLY_COOLDOWN", actionVersionNo=1, targetBindingCode=targets[0]["bindingCode"],
                 parameters=[parameter("DURATION", "DURATION", "1800000")]),
            dict(sequenceNo=2, actionCode="ADD_RISK_TAG", actionVersionNo=1, targetBindingCode=targets[0]["bindingCode"],
                 parameters=[parameter("TAG", "STRING", tag, index) for index, tag in enumerate(["SECOND", "FIRST", "SECOND"], 1)])]
        key = uuid.uuid4().hex
        headers = {"Idempotency-Key": key}
        probe._denied(denied, "POST", SAVE, request, key)
        saved = browser.request("POST", SAVE, request, extra_headers=headers)
        probe._validate_rule_create(saved, request)
        canonical = copy.deepcopy(request)
        canonical["rules"][0]["additionalActions"][1]["parameters"] = [parameter("TAG", "STRING", tag, index) for index, tag in enumerate(["FIRST", "SECOND"], 1)]
        detail = browser.request("GET", SAVE + "/" + saved["ruleSetVersionId"])
        probe._validate_rule_detail(detail, saved, canonical, "DRAFT")
        require(saved == browser.request("POST", SAVE, canonical, extra_headers=headers), "CONFIGURATION_REPLAY_CHANGED")
        probe._expect_problem(browser, "POST", SAVE + "/" + saved["ruleSetVersionId"] + "/freeze",
                              {"expectedContentHash": saved["contentHash"]}, 400, "BUSINESS_CHECK_FAILED", uuid.uuid4().hex)
        require(browser.request("GET", SAVE + "/" + saved["ruleSetVersionId"])["status"] == "DRAFT", "CONFIGURATION_WAS_FROZEN")
        invalid = copy.deepcopy(request)
        invalid["ruleSetCode"] = probe.rule_set_code_factory()
        invalid["rules"][0]["additionalActions"][0]["targetBindingCode"] = "CURRENT_CONSUMER_V1"
        probe._expect_problem(browser, "POST", SAVE, invalid, 400, "BUSINESS_CHECK_FAILED", uuid.uuid4().hex)
        # A new immutable version round-trips the same configuration using the saved base coordinates.
        edited = copy.deepcopy(canonical)
        edited["baseRuleSetVersionId"], edited["baseContentHash"] = saved["ruleSetVersionId"], saved["contentHash"]
        edited["rules"][0]["additionalActions"][0]["parameters"][0]["literalValue"] = "3600000"
        updated = browser.request("POST", SAVE, edited, extra_headers={"Idempotency-Key": uuid.uuid4().hex})
        require(updated["versionNo"] == 2 and updated["contentHash"] != saved["contentHash"], "CONFIGURATION_NEW_VERSION_INVALID")
        probe._validate_rekeyed_rules(browser.request("GET", SAVE + "/" + updated["ruleSetVersionId"])["rules"], edited["rules"])
        require(browser.request("GET", SAVE + "/" + saved["ruleSetVersionId"])["status"] == "RETIRED", "CONFIGURATION_PRIOR_VERSION_NOT_RETIRED")
        rejection = probe._minimal_save_request(probe.rule_set_code_factory())
        rejection["rules"][0].update(action={"actionCode": "DENY_CHECKOUT", "actionVersionNo": 1},
                                      reasonCode="MANUAL_REJECTION", reasonMessage="人工拒绝说明")
        key = uuid.uuid4().hex
        denied_config = browser.request("POST", SAVE, rejection, extra_headers={"Idempotency-Key": key})
        probe._validate_rule_detail(browser.request("GET", SAVE + "/" + denied_config["ruleSetVersionId"]), denied_config, rejection, "DRAFT")
        changed = copy.deepcopy(rejection)
        changed["rules"][0]["reasonMessage"] = "另一条说明"
        probe._expect_problem(browser, "POST", SAVE, changed, 409, "CONCURRENT_REQUEST", key)
        frozen = browser.request("POST", SAVE + "/" + denied_config["ruleSetVersionId"] + "/freeze",
                                 {"expectedContentHash": denied_config["contentHash"]}, extra_headers={"Idempotency-Key": uuid.uuid4().hex})
        require(frozen["status"] == "FROZEN", "TERMINAL_CONFIGURATION_FREEZE_INVALID")
        require(runtime_counts() == before, "CONFIGURATION_CREATED_RUNTIME_FACTS")
    finally:
        runner._close_sessions(administrator, actors)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="Explicitly create and verify synthetic configuration facts")
    parser.add_argument("--base-url", default="http://localhost:28080")
    options = parser.parse_args(argv)
    if not options.verify:
        return 0
    try:
        verify(options.base_url)
    except (Exception, SystemExit):
        print("RISK_V1_CONFIGURATION_SMOKE_FAILED", file=sys.stderr)
        return 1
    print("RISK_V1_CONFIGURATION_SMOKE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
