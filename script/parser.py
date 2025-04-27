import json
from collections import defaultdict

def parse_coverage_log_better(input_log_path, output_json_path):
    with open(input_log_path, 'r') as f:
        lines = [line.strip() for line in f if line.strip()]

    location_map = defaultdict(lambda: {"exercised": [], "subconditions": defaultdict(list)})

    current_test = None

    for line in lines:
        if line.startswith("=== START TEST:"):
            start_idx = line.find('<')
            end_idx = line.find('>')
            if start_idx != -1 and end_idx != -1:
                full_test_id = line[start_idx + 1:end_idx]
                current_test = full_test_id.split(":")[-1].strip()
        elif line.startswith("=== END TEST:"):
            current_test = None
        elif current_test is not None:
            if '"event":"EXERCISED"' in line:
                file = extract_between(line, '"file":"', '"')
                lineno = extract_between(line, '"line":"', '"')
                location = f"{file}:{lineno}"
                location_map[location]["exercised"].append(current_test)
            elif '"event":"SUBCONDITION_CHECKED"' in line:
                file = extract_between(line, '"file":"', '"')
                lineno = extract_between(line, '"line":"', '"')
                index = extract_between(line, '"index":', '}').replace('"', '').strip()
                location = f"{file}:{lineno}"
                location_map[location]["subconditions"][index].append(current_test)


    final_map = {}
    for loc, data in location_map.items():
        final_map[loc] = {
            "exercised": {
                "cnt": len(set(data["exercised"])),
                "testcases": sorted(set(data["exercised"]))
            },
            "subconditions": {
                idx: {
                    "cnt": len(set(tests)),
                    "testcases": sorted(set(tests))
                }
                for idx, tests in data["subconditions"].items()
            }
        }

    with open(output_json_path, "w") as f:
        json.dump(final_map, f, indent=2)

def extract_between(text, start_token, end_token):
    start_idx = text.find(start_token)
    if start_idx == -1:
        return ""
    start_idx += len(start_token)
    end_idx = text.find(end_token, start_idx)
    if end_idx == -1:
        return ""
    return text[start_idx:end_idx]

def main():
    input_log = "/Users/yzhou29/git/trace/joda_time-2.10.3/coverage.log"
    output_json = "location_based_coverage_final.json"
    parse_coverage_log_better(input_log, output_json)

if __name__ == "__main__":
    main()