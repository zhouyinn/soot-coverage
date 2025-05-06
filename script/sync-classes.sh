#!/bin/bash

# Ensure a project path is passed as an argument
if [ -z "$1" ]; then
  echo "⚠️  Please provide the target project path as an argument."
  echo "Usage: $0 <target-project-path>"
  exit 1
fi

# Define the target project from input argument
PROJ=$1
LOGGER_CLASS="../soot-instrument/target/classes/Logger.class"

echo "🔎 Scanning modules under: $PROJ"

# Function to copy matching .class files only
copy_matching_classes() {
  local SRC=$1
  local DST=$2
  if [ ! -d "$SRC" ]; then
    echo "⚠️  No instrumented classes found at $SRC, skipping."
    return
  fi
  if [ ! -d "$DST" ]; then
    echo "⚠️  No destination classes at $DST, skipping."
    return
  fi
  echo "→ Syncing from $SRC to $DST"
  (cd "$SRC" && find . -name "*.class") | while read -r classfile; do
    if [ -f "$DST/$classfile" ]; then
      mkdir -p "$(dirname "$DST/$classfile")"
      cp "$SRC/$classfile" "$DST/$classfile"
      echo "  ✔ $classfile"
    fi
  done
}

# 1️⃣ Loop through all subdirectories (modules)
find "$PROJ" -mindepth 1 -maxdepth 1 -type d | while read -r MODULE_DIR; do
  MODULE_NAME=$(basename "$MODULE_DIR")

  echo "========================================="
  echo "🔧 Processing module: $MODULE_NAME"

  SRC_CLASSES="$MODULE_DIR/instrumented-classes"
  SRC_TEST_CLASSES="$MODULE_DIR/instrumented-test-classes"
  DST_CLASSES="$MODULE_DIR/target/classes"
  DST_TEST_CLASSES="$MODULE_DIR/target/test-classes"

  # Copy instrumented classes to destination project
  echo "⚙️  Overwriting original class files with instrumented ones in module: $MODULE_NAME"
  copy_matching_classes "$SRC_CLASSES" "$DST_CLASSES"
  copy_matching_classes "$SRC_TEST_CLASSES" "$DST_TEST_CLASSES"

  # ✅ Ensure Logger.class is copied to each module's classes
  if [ -f "$LOGGER_CLASS" ]; then
    if [ -d "$DST_CLASSES" ]; then
      echo "→ Copying Logger.class to $DST_CLASSES"
      cp "$LOGGER_CLASS" "$DST_CLASSES/"
    else
      echo "⚠️  Skipping Logger.class copy: $DST_CLASSES does not exist."
    fi
  else
    echo "⚠️ Logger.class not found at $LOGGER_CLASS"
  fi
done

echo "========================================="
echo "✅ Done: processed all modules, overwrote instrumented class files and injected Logger.class where applicable."