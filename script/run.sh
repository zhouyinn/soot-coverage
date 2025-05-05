#!/bin/bash

# Ensure a root directory and project name are provided as arguments
if [ -z "$1" ]; then
    echo "Usage: $0 <root-directory> <project-name>"
    exit 1
fi

ROOT_DIR="/Users/yzhou29/git/trace"  # Fixed the space issue
PROJECT_NAME=$1

PROJECT_DIR="$ROOT_DIR/$PROJECT_NAME"

# Step 1: Clean Soot (Run clean-soot.sh)
echo "Cleaning Soot for project: $PROJECT_DIR"
bash script/clean-soot.sh "$PROJECT_DIR" || { echo "Failed to clean Soot"; exit 1; }

# Step 2: Compile project
echo "Compiling project: $PROJECT_NAME"
cd "$PROJECT_DIR" || { echo "Project directory '$PROJECT_DIR' not found"; exit 1; }

# Run Maven commands for clean, compile, and test-compile
echo "Running mvn clean compile test-compile"
mvn clean compile test-compile || { echo "Maven compile failed"; exit 1; }

cd "$ROOT_DIR/soot-instrument" || { echo "Project directory soot-instrument not found"; exit 1; }

cp Logger.class $PROJECT_DIR/target/classes

# Step 3: Run Maven exec:java with arguments
echo "Running instrumented class generation"
mvn clean compile exec:java -Dexec.args="$PROJECT_DIR $PROJECT_DIR/enforcing_statements.txt $PROJECT_DIR/monitored_fields.txt" 2>&1 | tee instrumented.log || { echo "Maven exec failed"; exit 1; }

# Step 4: Sync classes
echo "Syncing classes with sync-classes.sh"
bash script/sync-classes.sh "$PROJECT_DIR" || { echo "Failed to sync classes"; exit 1; }

echo "Process completed successfully."