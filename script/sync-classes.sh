#!/bin/bash

# Ensure a project path is passed as an argument
if [ -z "$1" ]; then
  echo "⚠️  Please provide the target project path as an argument."
  echo "Usage: $0 <target-project-path>"
  exit 1
fi

# Define the target project from input argument
PROJ=$1
SRC_CLASSES="$PROJ/instrumented-classes"
SRC_TEST_CLASSES="$PROJ/instrumented-test-classes"
DST_CLASSES="$PROJ/target/classes"
DST_TEST_CLASSES="$PROJ/target/test-classes"
LOGGER_CLASS="../statement-coverage/target/classes/Logger.class"

# Function to copy matching .class files only
copy_matching_classes() {
  local SRC=$1
  local DST=$2
  echo "→ Syncing from $SRC to $DST"
  (cd "$SRC" && find . -name "*.class") | while read -r classfile; do
    if [ -f "$DST/$classfile" ]; then
      mkdir -p "$(dirname "$DST/$classfile")"
      cp "$SRC/$classfile" "$DST/$classfile"
      echo "  ✔ $classfile"
    fi
  done
}

# Copy instrumented classes to destination project
echo "⚙️  Overwriting original class files with instrumented ones..."
copy_matching_classes "$SRC_CLASSES" "$DST_CLASSES"
copy_matching_classes "$SRC_TEST_CLASSES" "$DST_TEST_CLASSES"

# ✅ Ensure Logger.class is copied from statement-coverage into the test runtime
if [ -f "$LOGGER_CLASS" ]; then
  echo "→ Copying Logger.class to $DST_CLASSES"
  cp "$LOGGER_CLASS" "$DST_CLASSES/"
else
  echo "⚠️ Logger.class not found at $LOGGER_CLASS"
fi


echo "✅ Done: only overwrote instrumented class files and injected Logger.class"