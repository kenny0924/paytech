"""Static checks for the complete NI design fixture, not a production interpreter."""

from copy import deepcopy
from pathlib import Path
import json
import re


BASE = Path(__file__).resolve().parent


def read(name):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "DUPLICATE_JSON_PROPERTY")
            result[key] = value
        return result
    return json.loads((BASE / name).read_text(), object_pairs_hook=unique)


def require(condition, code):
    if not condition:
        raise ValueError(code)


def keys(value, expected):
    require(isinstance(value, dict) and set(value) == set(expected.split()), "OBJECT_FIELDS")


def size(value):
    return len(json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode())


def object_type(fields):
    return {"type": "OBJECT", "fields": fields, "dataClass": "INTERNAL", "nullable": False}


def sensitive(schema):
    if schema["type"] in ("BYTES", "PROTECTED_HANDLE") or schema.get("dataClass") == "PROTECTED":
        return True
    if schema["type"] == "OBJECT":
        return any(sensitive(value) for value in schema["fields"].values())
    if schema["type"] == "ARRAY":
        return sensitive(schema["items"])
    return False


def field_shape(field, item=False, depth=1):
    base = {"type", "nullable", "dataClass"} | (set() if item else {"required", "displayName"})
    extra = {
        "STRING": {"minLength", "maxLength"}, "INTEGER": {"minValue", "maxValue"},
        "DECIMAL": {"precision", "scale", "minValue", "maxValue", "unit"},
        "OBJECT": {"fields"}, "ARRAY": {"items", "minItems", "maxItems"},
        "BYTES": {"maxBytes"}, "PROTECTED_HANDLE": {"purpose", "allowedSinks"},
        "BOOLEAN": set(),
    }
    require(depth <= 8 and field["type"] in extra, "FIELD_TYPE")
    require(base <= field.keys() and field.keys() <= base | extra[field["type"]] | {"unit"}, "FIELD_METADATA")
    if field["type"] == "OBJECT":
        for value in field["fields"].values():
            field_shape(value, depth=depth + 1)
    if field["type"] == "ARRAY":
        require(0 <= field["minItems"] <= field["maxItems"] <= 128, "ARRAY_BOUND")
        field_shape(field["items"], item=True, depth=depth + 1)


def validate(definition, scheme, registry, catalog):
    keys(definition, "inputDefinition requestSteps transportDefinition responseSteps resultRules outputDefinition")
    keys(scheme, "kind capabilityCode inputDefinition outputDefinition parameterDefinition fixedParameters credentialPurpose")
    require(size(definition) <= 49152 and size(scheme) <= 49152, "DEFINITION_SIZE")
    capabilities = {c["capabilityCode"]: c for c in catalog["capabilities"]}
    require(len(capabilities) == len(catalog["capabilities"]), "DUPLICATE_CAPABILITY")
    functions = {f["functionCode"]: f for f in registry["functions"]}
    profiles = {p["profileCode"]: p for p in catalog["protocolProfiles"]["response"]}
    contexts = {c["contextType"]: c for c in registry["contextTypes"]}
    keys(definition["inputDefinition"], "contextType requiredPaths")
    context = contexts[definition["inputDefinition"]["contextType"]]
    context_schema = object_type(context["fields"])
    runtime = {tuple(r["path"]): r["definition"] for r in catalog["runtimeInputs"]}
    pure = {p["name"]: p for p in registry["expressionProfile"]["pureFunctions"]}
    outputs, resolved, origins = {}, {}, {}
    expression_count = 0
    function_count = 0
    phase = "REQUEST"

    def path_type(schema, path):
        require(isinstance(path, list) and len(path) <= 16, "PATH_SHAPE")
        for segment in path:
            require(isinstance(segment, str) and 0 < len(segment) <= 128, "PATH_SEGMENT")
            require(schema["type"] == "OBJECT" and segment in schema["fields"], "UNKNOWN_PATH")
            schema = schema["fields"][segment]
        return schema

    def binding_type(binding, variable=False, condition=False):
        nonlocal expression_count
        source = binding["source"]
        if source in ["CONTEXT", "RUNTIME", "RESPONSE"]:
            keys(binding, "source path")
            path = binding["path"]
            require(bool(path) or (source == "CONTEXT" and variable), "EMPTY_PATH")
            if source == "CONTEXT":
                return path_type(context_schema, path)
            if source == "RUNTIME":
                require(tuple(path) in runtime, "RUNTIME_PATH")
                return runtime[tuple(path)]
            require(phase == "RESPONSE", "RESPONSE_PHASE")
            if path == ["status"]:
                return {"type": "INTEGER", "dataClass": "INTERNAL"}
            if path == ["headers", "content-type"]:
                return profiles["NI_REFUND_CAPTURE"]["decodeInputDefinition"]["contentType"]
            require(path == ["body"], "RESPONSE_PATH")
            return {"type": "BYTES", "maxBytes": 1048576, "dataClass": "PROTECTED"}
        if source == "STEP":
            keys(binding, "source stepCode path")
            require(binding["stepCode"] in outputs, "FORWARD_REFERENCE")
            require(bool(binding["path"]), "EMPTY_PATH")
            return path_type(outputs[binding["stepCode"]], binding["path"])
        if source == "LITERAL":
            keys(binding, "source valueType value")
            require(binding["valueType"] == "INTEGER" and re.fullmatch(r"-?[0-9]+", binding["value"]) is not None, "LITERAL_TYPE")
            return {"type": binding["valueType"], "dataClass": "INTERNAL"}
        require(source == "EXPRESSION" and not variable and not condition, "EXPRESSION_POSITION")
        keys(binding, "source engine expression variables")
        require(binding["engine"] == "AVIATOR", "EXPRESSION_ENGINE")
        require(0 < len(binding["expression"].encode()) <= 2048 and len(binding["variables"]) <= 16, "EXPRESSION_BOUND")
        expression_count += 1
        require(expression_count <= 128, "EXPRESSION_COUNT")
        variables = {name: binding_type(value, variable=True) for name, value in binding["variables"].items()}
        require(all(not sensitive(value) for value in variables.values()), "PROTECTED_EXPRESSION")
        used = set()

        def select(text):
            alias, *path = text.split(".")
            require(alias in variables, "UNKNOWN_VARIABLE")
            used.add(alias)
            return path_type(variables[alias], path)

        text = binding["expression"]
        if re.fullmatch(r"'[^']*'", text):
            result = {"type": "STRING", "dataClass": "INTERNAL"}
        elif re.fullmatch(r"[a-zA-Z]+(?:\.[a-zA-Z]+)+", text):
            result = select(text)
        else:
            match = re.fullmatch(r"([a-zA-Z]+\.[a-zA-Z]+)\(([^()]+)\)", text)
            require(match is not None and match[1] in pure, "EXPRESSION_NOT_IN_FIXTURE_PROFILE")
            function = pure[match[1]]
            args = [arg.strip() for arg in match[2].split(",")]
            require(len(args) == len(function["argumentOrder"]), "EXPRESSION_ARGUMENTS")
            for name, arg in zip(function["argumentOrder"], args):
                require(select(arg)["type"] == function["inputDefinition"][name]["type"], "EXPRESSION_ARGUMENT_TYPE")
            result = function["outputDefinition"]["value"]
        require(used == variables.keys(), "UNUSED_VARIABLE")
        return result

    def compatible(actual, expected):
        require(actual["type"] == expected["type"], "VALUE_TYPE")
        if expected["type"] == "PROTECTED_HANDLE":
            require(actual["purpose"] == expected["purpose"] and actual["allowedSinks"] == expected["allowedSinks"], "PROTECTED_SINK")
        if expected["type"] == "OBJECT":
            require(set(actual["fields"]) == set(expected["fields"]), "OBJECT_TYPE_FIELDS")
            for key in expected["fields"]:
                compatible(actual["fields"][key], expected["fields"][key])

    paths = definition["inputDefinition"]["requiredPaths"]
    require(len(paths) <= 128 and len({tuple(p) for p in paths}) == len(paths), "REQUIRED_PATHS")
    for path in paths:
        require(bool(path), "EMPTY_REQUIRED_PATH")
        path_type(context_schema, path)
    bearer = capabilities["BEARER_AUTHORIZATION"]["contract"]
    require(scheme["kind"] == "CAPABILITY" and scheme["capabilityCode"] == "BEARER_AUTHORIZATION", "SCHEME_CAPABILITY")
    require(scheme["inputDefinition"] == bearer["inputDefinition"] and scheme["outputDefinition"] == bearer["outputDefinition"], "SCHEME_TYPE_OR_SINK")
    require(scheme["fixedParameters"] == scheme["parameterDefinition"] == {} and scheme["credentialPurpose"] == "accessToken", "SCHEME_PARAMETERS")

    def process_step(step):
        nonlocal function_count
        extra = "capabilityCode" if step["kind"] == "CAPABILITY" else "schemeCode"
        keys(step, "stepCode kind inputBindings parameters outputBindings " + extra)
        code = step["stepCode"]
        require(re.fullmatch(r"[a-z][A-Za-z0-9]{0,63}", code) is not None and code not in outputs, "STEP_ID")
        if step["kind"] == "SCHEME":
            require(step["schemeCode"] == "NI_PAYMENT_BEARER", "UNKNOWN_SCHEME")
            require(step["parameters"] == {}, "FIXED_PARAMETER_OVERRIDE")
            capability = scheme["capabilityCode"]
        else:
            require(step["kind"] == "CAPABILITY", "STEP_KIND")
            capability = step["capabilityCode"]
        require(capability in capabilities, "UNKNOWN_CAPABILITY")
        require(capabilities[capability]["usage"] == "STEP" and phase in capabilities[capability]["phases"], "CAPABILITY_PHASE")
        inputs, params = step["inputBindings"], step["parameters"]
        actual = {key: binding_type(value) for key, value in inputs.items()}
        if capability == "CALL_SYSTEM_FUNCTION":
            keys(params, "functionCode")
            require(params["functionCode"] in functions, "UNKNOWN_FUNCTION")
            function = functions[params["functionCode"]]
            require(context["contextType"] in function["allowedContextTypes"], "FUNCTION_CONTEXT")
            require(function["allowedChannelCodes"] is None or "NI" in function["allowedChannelCodes"], "FUNCTION_CHANNEL")
            expected = function["inputDefinition"]
            result = {"value": object_type(function["outputDefinition"])}
            function_count += 1
            require(function_count <= 8, "FUNCTION_COUNT")
            origins[code] = params["functionCode"]
        elif capability == "OBJECT_MAPPING":
            keys(params, "fields")
            require(0 < len(params["fields"]) <= 128, "MAPPING_COUNT")
            expected, target_fields, used_paths = {}, {}, []
            for field in params["fields"]:
                keys(field, "inputName targetPath targetDefinition")
                name, path, target = field["inputName"], field["targetPath"], field["targetDefinition"]
                require(name not in expected and re.fullmatch(r"[a-z][A-Za-z0-9]{0,63}", name) is not None, "MAPPING_INPUT")
                require(0 < len(path) <= 16 and all(isinstance(x, str) and x for x in path), "MAPPING_PATH")
                require(all(path[:len(old)] != old and old[:len(path)] != path for old in used_paths), "TARGET_PATH_CONFLICT")
                used_paths.append(path)
                field_shape(target)
                require(not sensitive(target), "MAPPING_PROTECTED_TYPE")
                expected[name] = target
                cursor = target_fields
                for segment in path[:-1]:
                    cursor = cursor.setdefault(segment, object_type({}))["fields"]
                cursor[path[-1]] = target
            result = {"value": object_type(target_fields)}
        elif capability == "ENCODE_JSON":
            require(params == {"charset": "UTF-8"}, "JSON_ENCODING")
            require(set(actual) == {"value"} and actual["value"]["type"] == "OBJECT" and not sensitive(actual["value"]), "JSON_INPUT")
            expected = actual
            result = {"bytes": {"type": "BYTES", "dataClass": "INTERNAL", "maxBytes": 1048576}}
        elif capability == "BEARER_AUTHORIZATION":
            require(params == {}, "BEARER_PARAMETERS")
            expected, result = bearer["inputDefinition"], bearer["outputDefinition"]
        elif capability in ["DECODE_JSON_RESPONSE", "VALIDATE_AND_PROJECT"]:
            keys(params, "profileCode")
            require(params["profileCode"] in profiles, "UNKNOWN_PROFILE")
            profile = profiles[params["profileCode"]]
            require("NI" in profile["allowedChannelCodes"] and context["contextType"] in profile["allowedContextTypes"], "PROFILE_SCOPE")
            if capability == "DECODE_JSON_RESPONSE":
                expected, result = profile["decodeInputDefinition"], profile["decodeOutputDefinition"]
                origins[code] = params["profileCode"]
            else:
                expected, result = profile["projectionInputDefinition"], profile["projectionOutputDefinition"]
                require(inputs["document"]["source"] == "STEP" and origins.get(inputs["document"]["stepCode"]) == params["profileCode"], "PROFILE_DOCUMENT_ORIGIN")
                require(inputs["expectedTarget"]["source"] == "STEP" and origins.get(inputs["expectedTarget"]["stepCode"]) == "READ_REFUND_TARGET", "TARGET_ORIGIN")
        else:
            raise ValueError("CRYPTO_PROFILE_NOT_REGISTERED")
        require(set(actual) == set(expected), "STEP_INPUTS")
        for name in expected:
            compatible(actual[name], expected[name])
        aliases = {}
        require(bool(step["outputBindings"]), "STEP_OUTPUTS")
        for alias, path in step["outputBindings"].items():
            require(re.fullmatch(r"[a-z][A-Za-z0-9]{0,63}", alias) is not None and bool(path), "OUTPUT_ALIAS")
            aliases[alias] = path_type(object_type(result), path)
        outputs[code] = object_type(aliases)
        resolved[code] = capability

    require(len(definition["requestSteps"]) <= 32 and len(definition["responseSteps"]) <= 32, "STEP_COUNT")
    for step in definition["requestSteps"]:
        process_step(step)
    transport = definition["transportDefinition"]
    keys(transport, "protocol method endpointCode pathTemplate pathBindings queryBindings headers body connectTimeoutMs responseTimeoutMs totalTimeoutMs")
    require(transport["protocol"] == "HTTP" and transport["method"] == "POST" and transport["endpointCode"] == "NI_TRANSACTIONS", "TRANSPORT_PROFILE")
    template = transport["pathTemplate"]
    require(template.startswith("/") and not template.startswith("//") and not any(x in template for x in ["?", "#", "\\", ":", "//"]), "TRANSPORT_PATH")
    require(all(segment not in [".", ".."] for segment in template.split("/")), "TRANSPORT_PATH")
    require(set(re.findall(r"\{([a-zA-Z]+)\}", template)) == set(transport["pathBindings"]), "PATH_PARAMETERS")
    for binding in transport["pathBindings"].values():
        require(binding_type(binding)["type"] == "STRING", "PATH_VALUE_TYPE")
    require(transport["queryBindings"] == [], "UNEXPECTED_NI_QUERY")
    names = set()
    for header in transport["headers"]:
        keys(header, "name values")
        name = header["name"].lower()
        require(name not in names and re.fullmatch(r"[A-Za-z-]+", name) is not None, "DUPLICATE_OR_INVALID_HEADER")
        names.add(name)
        require(1 <= len(header["values"]) <= 16, "HEADER_COUNT")
        for value in header["values"]:
            actual = binding_type(value)
            if name == "authorization":
                require(actual["type"] == "PROTECTED_HANDLE" and actual["allowedSinks"] == ["HTTP_HEADER:authorization"], "AUTHORIZATION_SOURCE")
            else:
                require(actual["type"] == "STRING", "HEADER_VALUE_TYPE")
    require(names == {"authorization", "content-type", "accept"}, "NI_HEADERS")
    require(binding_type(transport["body"])["type"] == "BYTES", "HTTP_BODY_BYTES")
    require(100 <= transport["connectTimeoutMs"] <= 10000 and 100 <= transport["responseTimeoutMs"] <= 30000 and 1000 <= transport["totalTimeoutMs"] <= 60000, "TIMEOUT_BUDGET")
    phase = "RESPONSE"
    for step in definition["responseSteps"]:
        process_step(step)
    result_rules = definition["resultRules"]
    keys(result_rules, "rules fallbackResultCode")
    require(result_rules["fallbackResultCode"] == "UNRECOGNIZED", "FALLBACK_RESULT")
    node_count = 0

    def condition(value, depth=1):
        nonlocal node_count
        node_count += 1
        require(depth <= 8 and node_count <= 256, "CONDITION_BOUND")
        op = value["op"]
        if op in ["EQ", "NE"]:
            keys(value, "op left right")
            left, right = binding_type(value["left"], condition=True), binding_type(value["right"], condition=True)
            require(left["type"] == right["type"] and not sensitive(left) and not sensitive(right), "CONDITION_TYPE")
        elif op in ["ALL", "ANY"]:
            keys(value, "op children")
            require(0 < len(value["children"]) <= 32, "CONDITION_CHILDREN")
            for child in value["children"]:
                condition(child, depth + 1)
        elif op == "NOT":
            keys(value, "op child")
            condition(value["child"], depth + 1)
        elif op == "EXISTS":
            keys(value, "op value")
            require(not sensitive(binding_type(value["value"], condition=True)), "CONDITION_TYPE")
        else:
            raise ValueError("CONDITION_OP")
    require(0 < len(result_rules["rules"]) <= 64, "RESULT_RULE_COUNT")
    results = {result_rules["fallbackResultCode"]}
    for rule in result_rules["rules"]:
        keys(rule, "condition resultCode")
        condition(rule["condition"])
        results.add(rule["resultCode"])
    require(len(definition["outputDefinition"]) == len(results) and {r["resultCode"] for r in definition["outputDefinition"]} == results, "RESULT_BRANCHES")
    for branch in definition["outputDefinition"]:
        keys(branch, "resultCode fields")
        require(len(branch["fields"]) <= 128, "OUTPUT_COUNT")
        require(len({f["name"] for f in branch["fields"]}) == len(branch["fields"]), "OUTPUT_NAMES")
        for field in branch["fields"]:
            keys(field, "name valueType required binding")
            actual = binding_type(field["binding"])
            require(field["valueType"] == actual["type"] and not sensitive(actual), "OUTPUT_TYPE")
    require(results == {"UNRESOLVED", "UNRECOGNIZED"}, "NI_RESULT_ASSOCIATION")
    return dict(apiCompactBytes=size(definition), schemeCompactBytes=size(scheme), resolvedSteps=resolved,
                expressionBindings=expression_count, systemFunctionCalls=function_count)


def main():
    definition = read("ni-refund-api-definition.json")
    scheme = read("ni-payment-bearer-scheme-definition.json")
    registry = read("registry-catalog.example.json")
    catalog = read("protocol-capabilities.example.json")
    result = validate(definition, scheme, registry, catalog)
    mutations = [
        ("OBJECT_FIELDS", lambda d, s: d.update(extension={})),
        ("UNKNOWN_SCHEME", lambda d, s: d["requestSteps"][3].update(schemeCode="MISSING")),
        ("FIXED_PARAMETER_OVERRIDE", lambda d, s: d["requestSteps"][3]["parameters"].update(prefix="Other ")),
        ("FORWARD_REFERENCE", lambda d, s: d["requestSteps"][2]["inputBindings"]["value"].update(stepCode="observedFacts")),
        ("TARGET_PATH_CONFLICT", lambda d, s: d["requestSteps"][1]["parameters"]["fields"][1].update(targetPath=["amount"])),
        ("HTTP_BODY_BYTES", lambda d, s: d["transportDefinition"].update(body={"source":"STEP","stepCode":"requestBody","path":["object"]})),
        ("AUTHORIZATION_SOURCE", lambda d, s: d["transportDefinition"]["headers"][0].update(values=[{"source":"EXPRESSION","engine":"AVIATOR","expression":"'fake'","variables":{}}])),
        ("SCHEME_TYPE_OR_SINK", lambda d, s: s["outputDefinition"]["headerValue"].update(allowedSinks=["HTTP_HEADER:other"])),
        ("UNKNOWN_PROFILE", lambda d, s: d["responseSteps"][0]["parameters"].update(profileCode="UNKNOWN")),
        ("CAPABILITY_PHASE", lambda d, s: d["requestSteps"][0].update(capabilityCode="DECODE_JSON_RESPONSE")),
        ("CONDITION_OP", lambda d, s: d["resultRules"]["rules"][0]["condition"].update(op="SCRIPT")),
        ("FALLBACK_RESULT", lambda d, s: d["resultRules"].update(fallbackResultCode="SUCCESS")),
        ("RESULT_BRANCHES", lambda d, s: d["outputDefinition"].pop()),
        ("OUTPUT_TYPE", lambda d, s: d["outputDefinition"][0]["fields"][0].update(valueType="INTEGER")),
        ("TIMEOUT_BUDGET", lambda d, s: d["transportDefinition"].update(totalTimeoutMs=61000)),
    ]
    rejected = []
    for expected, mutate in mutations:
        changed, changed_scheme = deepcopy(definition), deepcopy(scheme)
        mutate(changed, changed_scheme)
        try:
            validate(changed, changed_scheme, registry, catalog)
        except ValueError as error:
            require(str(error) == expected, "UNEXPECTED_REJECTION:" + str(error))
            rejected.append(expected)
        else:
            raise ValueError("MUTATION_NOT_REJECTED:" + expected)
    report = dict(scope="COMPLETE_NI_DEFINITION_DESIGN_STATIC_CHECK", status="PASSED", **result,
                  capabilityDescriptors=len(catalog["capabilities"]), requestSteps=len(definition["requestSteps"]),
                  responseSteps=len(definition["responseSteps"]), rejectedMutations=rejected,
                  approvedPaymentMethodProvided=False, managementRequestValidated=False,
                  aviatorCompiled=False, runtimeExecuted=False, databaseSqlExecuted=False,
                  channelRequestsSent=0)
    (BASE / "complete-definition-verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
