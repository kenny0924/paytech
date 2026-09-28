"""Validate finite design fixtures; never execute Aviator, system functions or HTTP."""

from copy import deepcopy
from decimal import Decimal, localcontext
from pathlib import Path
import json
import re


BASE = Path(__file__).resolve().parent


def read(name):
    return json.loads((BASE / name).read_text())


def require(condition, code):
    if not condition:
        raise ValueError(code)


def schema_check(fields):
    common = {"type", "required", "nullable", "displayName", "dataClass", "unit"}
    constraints = {
        "STRING": {"minLength", "maxLength"},
        "DECIMAL": {"precision", "scale", "minValue", "maxValue"},
        "INTEGER": {"minValue", "maxValue"},
    }
    for field in fields.values():
        kind = field["type"]
        require(kind in constraints, "UNEXPECTED_FIXTURE_TYPE")
        require(common - {"unit"} <= field.keys(), "MISSING_FIELD_METADATA")
        require(field.keys() <= common | constraints[kind], "UNKNOWN_FIELD_METADATA")
        require(field["required"] is True and field["nullable"] is False, "FIELD_PRESENCE")
        require(field["dataClass"] == "INTERNAL", "CLASSIFICATION")
        if kind == "STRING":
            require(0 <= field["minLength"] <= field["maxLength"] <= 255, "STRING_BOUND")
        else:
            require(Decimal(field["minValue"]) <= Decimal(field["maxValue"]), "NUMBER_BOUND")
        if kind == "DECIMAL":
            require(0 <= field["scale"] <= field["precision"] <= 38, "DECIMAL_BOUND")


def values_check(value, fields):
    require(set(value) == set(fields), "VALUE_FIELDS")
    for name, spec in fields.items():
        item = value[name]
        require(isinstance(item, str), "EXACT_WIRE_STRING")
        if spec["type"] == "STRING":
            require(spec["minLength"] <= len(item) <= spec["maxLength"], "VALUE_LENGTH")
            if name.endswith("Id") and spec["maxLength"] == 19:
                require(re.fullmatch(r"[1-9][0-9]*", item) is not None, "ID_FORMAT")
                require(int(item) <= 9223372036854775807, "ID_RANGE")
        else:
            require(re.fullmatch(r"-?[0-9]+(?:\.[0-9]+)?", item) is not None, "DECIMAL_TEXT")
            number = Decimal(item)
            require(Decimal(spec["minValue"]) <= number <= Decimal(spec["maxValue"]), "VALUE_RANGE")
            require(max(0, -number.as_tuple().exponent) <= spec["scale"], "VALUE_SCALE")


def validate_catalog(catalog):
    require(set(catalog) == {"contextTypes", "functions", "expressionProfile"}, "CATALOG_FRAGMENT")
    contexts = {c["contextType"]: c for c in catalog["contextTypes"]}
    require(len(contexts) == len(catalog["contextTypes"]) == 2, "DUPLICATE_CONTEXT")
    require(len({c["contextName"] for c in contexts.values()}) == 2, "DUPLICATE_CONTEXT_NAME")
    for context in contexts.values():
        schema_check(context["fields"])
        require(context["implementationAvailable"] is False, "CONTEXT_AVAILABILITY")
    functions = {f["functionCode"]: f for f in catalog["functions"]}
    require(len(functions) == len(catalog["functions"]) == 2, "DUPLICATE_FUNCTION")
    for function in functions.values():
        schema_check(function["inputDefinition"])
        schema_check(function["outputDefinition"])
        require(set(function["allowedContextTypes"]) <= contexts.keys(), "FUNCTION_CONTEXT")
        require(0 < function["timeoutMs"] <= 2000, "FUNCTION_TIMEOUT_BOUND")
        require(0 < function["maxResultBytes"] <= 65536, "FUNCTION_SIZE_BOUND")
        require(function["missingPolicy"] == "FAIL", "MISSING_POLICY")
        require(function["implementationAvailable"] is False, "FUNCTION_AVAILABILITY")
        require(len(function["identityChecks"]) >= 4, "FUNCTION_IDENTITY_RULES")
    pure = catalog["expressionProfile"]["pureFunctions"]
    require(len(pure) == 1 and pure[0]["name"] == "money.minorUnits", "MINIMAL_PURE_CATALOG")
    for function in pure:
        schema_check(function["inputDefinition"])
        schema_check(function["outputDefinition"])
        order = function["argumentOrder"]
        require(len(order) == len(set(order)) and set(order) == set(function["inputDefinition"]), "ARGUMENT_ORDER")
        require(order == ["amount", "currency"], "MONEY_ARGUMENT_ORDER")
        require(function["implementationAvailable"] is False, "PURE_AVAILABILITY")
    require(catalog["expressionProfile"]["implementationAvailable"] is False, "ENGINE_AVAILABILITY")
    require(len(json.dumps(catalog).encode()) < 262144, "CATALOG_SIZE")
    return contexts, functions, {f["name"]: f for f in pure}


def validate_mapping(catalog, mapping):
    contexts, functions, pure = validate_catalog(catalog)
    context = contexts[mapping["inputDefinition"]["contextType"]]
    context_schema = {"type": "OBJECT", "fields": context["fields"]}
    outputs = {}

    def path_get(schema, path):
        for part in path:
            require(schema["type"] == "OBJECT" and part in schema["fields"], "UNKNOWN_FIELD")
            schema = schema["fields"][part]
        return schema

    def binding_type(binding, variable=False):
        if binding["source"] == "CONTEXT":
            require(variable or bool(binding["path"]), "EMPTY_CONTEXT_PATH")
            return path_get(context_schema, binding["path"])
        if binding["source"] == "STEP":
            require(binding["stepCode"] in outputs, "FORWARD_REFERENCE")
            return path_get(outputs[binding["stepCode"]], binding["path"])
        require(not variable and binding["source"] == "EXPRESSION", "BINDING_SOURCE")
        require(binding["engine"] == "AVIATOR", "EXPRESSION_ENGINE")
        variables = {key: binding_type(value, True) for key, value in binding["variables"].items()}
        expression = binding["expression"]
        used = set()

        def selector(text):
            root, *path = text.split(".")
            require(root in variables, "UNKNOWN_VARIABLE")
            used.add(root)
            return path_get(variables[root], path)

        # Only recognize the three documented fixture forms. This is not an Aviator parser.
        if re.fullmatch(r"'[^']*'", expression):
            result = {"type": "STRING"}
        elif re.fullmatch(r"[A-Za-z]+(?:\.[A-Za-z]+)+", expression):
            result = selector(expression)
        else:
            match = re.fullmatch(r"([A-Za-z]+\.[A-Za-z]+)\(([^()]+)\)", expression)
            require(match is not None, "UNSUPPORTED_FIXTURE_EXPRESSION")
            function_name, args_text = match.groups()
            require(function_name in pure, "PURE_FUNCTION_NOT_ALLOWED")
            function = pure[function_name]
            args = [arg.strip() for arg in args_text.split(",")]
            require(len(args) == len(function["argumentOrder"]), "ARGUMENT_COUNT")
            for name, arg in zip(function["argumentOrder"], args):
                require(selector(arg)["type"] == function["inputDefinition"][name]["type"], "ARGUMENT_TYPE")
            result = function["outputDefinition"]["value"]
        require(used == variables.keys(), "UNUSED_VARIABLE")
        return result

    for path in mapping["inputDefinition"]["requiredPaths"]:
        path_get(context_schema, path)
    for step in mapping["requestSteps"]:
        require(step["stepCode"] not in outputs, "DUPLICATE_STEP")
        require(step["capabilityCode"] == "CALL_SYSTEM_FUNCTION" and step["kind"] == "CAPABILITY", "FUNCTION_STEP")
        require(step["parameters"]["functionCode"] in functions, "UNKNOWN_FUNCTION")
        function = functions[step["parameters"]["functionCode"]]
        require(context["contextType"] in function["allowedContextTypes"], "FUNCTION_SCOPE")
        require(function["allowedChannelCodes"] is None or mapping["channelCode"] in function["allowedChannelCodes"], "FUNCTION_SCOPE")
        require(set(step["inputBindings"]) == set(function["inputDefinition"]), "FUNCTION_ARGUMENTS")
        for name, value in step["inputBindings"].items():
            require(binding_type(value)["type"] == function["inputDefinition"][name]["type"], "FUNCTION_ARGUMENT_TYPE")
        require(step["outputBindings"] == {"target": ["value"]}, "FUNCTION_OUTPUT_WRAPPER")
        outputs[step["stepCode"]] = {"type": "OBJECT", "fields": {"target": {"type": "OBJECT", "fields": function["outputDefinition"]}}}
    targets = set()
    for field in mapping["fieldMappings"]:
        identity = (field["location"], tuple(field["targetPath"]))
        require(identity not in targets, "DUPLICATE_TARGET")
        targets.add(identity)
        require(binding_type(field["binding"])["type"] == field["targetDefinition"]["type"], "TARGET_TYPE")
    require(len(outputs) == 1, "ONE_EXPLICIT_FUNCTION_CALL")
    return len(targets)


def verify_money_vectors(fixture):
    for vector in fixture["moneyVectors"]:
        precision = fixture["currencyPrecisionFixture"].get(vector["currency"])
        with localcontext() as context:
            context.prec = 50
            if precision is None:
                error = "EXPRESSION_CURRENCY_UNAVAILABLE"
            else:
                number = Decimal(vector["amount"]) * Decimal(10) ** precision
                error = "EXPRESSION_PRECISION" if number != number.to_integral_value() else None
            if "error" in vector:
                require(error == vector["error"], "MONEY_VECTOR_ERROR")
            else:
                require(error is None and str(int(number)) == vector["expected"], "MONEY_VECTOR_VALUE")
    require(fixture["expectedRequestDisplay"]["bodyUtf8"] == '{"amount":{"currencyCode":"AED","value":1000}}', "REQUEST_JSON_NUMBER")


def main():
    catalog = read("registry-catalog.example.json")
    mapping = read("ni-refund-mapping.example.json")
    fixture = read("ni-refund-fixture.json")
    count = validate_mapping(catalog, mapping)
    contexts, functions, _ = validate_catalog(catalog)
    values_check(fixture["context"], contexts["REFUND"]["fields"])
    values_check(fixture["assumedFunctionResult"], functions["READ_REFUND_TARGET"]["outputDefinition"])
    values_check(fixture["assumedOriginalTransactionFacts"], functions["READ_ORIGINAL_CHANNEL_TRANSACTION"]["outputDefinition"])
    require(fixture["context"]["originalTransactionRefId"] == fixture["assumedFunctionResult"]["originalTransactionRefId"], "TARGET_ID")
    for index, name in enumerate(["outletId", "orderRef", "paymentRef", "captureId"]):
        require(mapping["fieldMappings"][index]["binding"]["expression"] == "result." + name, "PATH_BINDING")
        require(fixture["assumedFunctionResult"][name] in fixture["expectedRequestDisplay"]["url"], "REQUEST_PATH")
    verify_money_vectors(fixture)
    require(fixture["expectedOutput"]["resultCode"] == "UNRESOLVED", "NO_REFUND_SUCCESS_CLAIM")
    require(len(fixture["expectedOutput"]["outputs"]["refundRecords"]) == 2, "NO_FIRST_RECORD_SELECTION")
    for record in fixture["expectedOutput"]["outputs"]["refundRecords"]:
        require(record["amount"]["value"] == "1000", "FIRST_PARTY_INTEGER_STRING")

    rejected = []
    mutations = [
        ("UNKNOWN_FIELD", lambda c, m: m["fieldMappings"][0]["binding"].update(expression="result.noSuchField")),
        ("PURE_FUNCTION_NOT_ALLOWED", lambda c, m: m["fieldMappings"][5]["binding"].update(expression="system.query(refundContext.refundAmount, refundContext.currency)")),
        ("ARGUMENT_TYPE", lambda c, m: m["fieldMappings"][5]["binding"].update(expression="money.minorUnits(refundContext.currency, refundContext.refundAmount)")),
        ("FUNCTION_SCOPE", lambda c, m: m.update(channelCode="GEIDEA")),
        ("UNKNOWN_FUNCTION", lambda c, m: m["requestSteps"][0]["parameters"].update(functionCode="MISSING_FUNCTION")),
        ("FORWARD_REFERENCE", lambda c, m: m["fieldMappings"][0]["binding"]["variables"]["result"].update(stepCode="futureCall")),
        ("TARGET_TYPE", lambda c, m: m["fieldMappings"][5]["targetDefinition"].update(type="STRING")),
        ("ARGUMENT_ORDER", lambda c, m: c["expressionProfile"]["pureFunctions"][0].update(argumentOrder=["amount", "amount"])),
    ]
    for expected, mutate in mutations:
        changed_catalog, changed_mapping = deepcopy(catalog), deepcopy(mapping)
        mutate(changed_catalog, changed_mapping)
        try:
            validate_mapping(changed_catalog, changed_mapping)
        except ValueError as error:
            require(str(error) == expected, "UNEXPECTED_REJECTION:" + str(error))
            rejected.append(expected)
        else:
            raise ValueError("MUTATION_NOT_REJECTED:" + expected)
    report = {
        "scope": "DESIGN_FIXTURES_ONLY",
        "status": "PASSED",
        "contexts": len(contexts),
        "contextFields": sum(len(c["fields"]) for c in contexts.values()),
        "systemFunctions": len(functions),
        "pureFunctions": 1,
        "mappedFields": count,
        "explicitFunctionCalls": 1,
        "moneyExpectationVectors": len(fixture["moneyVectors"]),
        "rejectedMutations": rejected,
        "aviatorCompiled": False,
        "aviatorExecuted": False,
        "systemFunctionsExecuted": False,
        "channelRequestsSent": 0,
        "completeApiWireValidated": False,
    }
    (BASE / "registry-verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
