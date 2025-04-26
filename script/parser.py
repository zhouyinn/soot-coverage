import re
import json
from collections import defaultdict

def parse_test_blocks(log_path):
    result = {}
    current_test_id = None
    current_conditions = []
    buffer_context = []
    pending_lines = []

    with open(log_path, 'r') as f:
        for line in f:
            line = line.strip()

            if line.startswith("=== START TEST:"):
                match = re.search(r"<(.*?): void (.*?)\(\)>", line)
                if match:
                    class_name, method_name = match.groups()
                    current_test_id = f"{class_name}#{method_name}"
                    current_conditions = []
                    buffer_context = []
                    pending_lines = []

            elif line.startswith("=== END TEST:"):
                pending_lines = []
                buffer_context = []
                if current_test_id and current_conditions:
                    result[current_test_id] = current_conditions
                current_test_id = None
                current_conditions = []

            elif '[' in line and 'Condition:' in line and current_test_id:
                location_match = re.search(r'\[(.*?)\]', line)
                condition_match = re.search(r'Condition:\s*(.*)', line)

                if location_match and condition_match:
                    location = location_match.group(1)
                    raw_condition = condition_match.group(1)

                    # 1. Build variable values from pending_lines
                    variable_values = build_variable_values(pending_lines)

                    # 2. Substitute into the raw condition
                    resolved_condition = substitute_variables(raw_condition, variable_values)

                    # 3. Try to evaluate result
                    try:
                        result_value = eval(resolved_condition)
                    except Exception:
                        result_value = None

                    condition_info = {
                        "location": location,
                        "raw": raw_condition,
                        "context": list(pending_lines),
                        "resolved": resolved_condition,
                        "result": result_value,
                        "test_id": current_test_id
                    }

                    current_conditions.append(condition_info)

                pending_lines = []

            elif current_test_id:
                pending_lines.append(line)

    return result

def build_variable_values(context_lines):
    """Build a dict of variable name -> value based on context."""
    var_values = {}
    for line in context_lines:
        m = re.match(r'(.*?)\s*(==|!=)\s*(.*)', line)
        if m:
            var, op, val = m.groups()
            var = var.strip()
            val = val.strip()

            if val == 'null':
                val = 'None'

            if op == '!=':
                # if var != null → var = "notnull"
                var_values[var] = '"notnull"'
            else:
                # if var == null → var = None
                var_values[var] = 'None'
            continue

        m = re.match(r'(.*?)\s*=\s*(.*)', line)
        if m:
            var, val = m.groups()
            var = var.strip()
            val = val.strip()
            if val == 'null':
                val = 'None'
            var_values[var] = val

    return var_values

def substitute_variables(expression, var_values):
    """Replace variables in the expression based on the context mapping."""
    resolved = expression
    for var, val in var_values.items():
        if isinstance(val, str) and val.startswith("NOT_"):
            # special handling for "!="
            real_val = val.replace("NOT_", "")
            resolved = resolved.replace(var, f"({var} != {real_val})")
        else:
            resolved = resolved.replace(var, val)
    return resolved

def order_by_location(testblocks):
    ordered = defaultdict(list)

    for test_id, conditions in testblocks.items():
        for condition in conditions:
            loc = condition.pop('location')
            ordered[loc].append(condition)

    final_result = {}
    for loc, conds in ordered.items():
        test_ids = {cond['test_id'] for cond in conds}
        results = {cond['result'] for cond in conds}

        final_result[loc] = {
            "testcases_cnt": len(test_ids),
            "results": list(results),
            "conditions": conds,
        }
    return final_result

if __name__ == "__main__":
    input_log = "/Users/yzhou29/git/trace/joda_time-2.10.3/coverage.log"
    output_json = "modeled_conditions.json"

    testblocks = parse_test_blocks(input_log)
    ordered_conditions = order_by_location(testblocks)

    with open(output_json, "w") as f:
        json.dump(ordered_conditions, f, indent=4)

    print(f"✅ Processed and wrote output with evaluation to {output_json}")