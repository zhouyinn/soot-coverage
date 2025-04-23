#!/bin/bash

if [ -z "$1" ]; then
  echo "❌ Usage: $0 <target-project-path>"
  exit 1
fi

TARGET_PROJ="$1"

echo "🧼 Cleaning Soot output and compiled files in: $TARGET_PROJ"

# Clean instrumented and Jimple output inside target project
rm -rf "$TARGET_PROJ/jimple-out"
rm -rf "$TARGET_PROJ/jimple-test-out"
rm -rf "$TARGET_PROJ/instrumented-classes"
rm -rf "$TARGET_PROJ/instrumented-test-classes"
rm -rf "$TARGET_PROJ/target"

# Delete all .class and .log files recursively from target project
find "$TARGET_PROJ" -name "*.class" -type f -delete
find "$TARGET_PROJ" -name "*.log" -type f -delete

echo "✅ Clean complete for: $TARGET_PROJ"