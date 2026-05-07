#!/bin/bash

# [CRITICAL] Check if build_counter.txt exists before reading it to avoid errors
if [[ ! -f "build_counter.txt" ]]; then
    echo "Error: build_counter.txt does not exist."
    exit 1
fi

# Read current counter
COUNTER=$(cat build_counter.txt)

# [MEDIUM] Check if gradlew exists before running it
if [[ ! -x "./gradlew" ]]; then
    echo "Error: gradlew does not exist or is not executable."
    exit 1
fi

# Build the APK
./gradlew clean assembleDebug || { echo "Build failed. Please check the build logs for more details."; exit 1; }

# Check if APK was generated
if [[ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]]; then
    echo "Error: APK not found after build."
    exit 1
fi

# Copy and rename with counter and timestamp
TIMESTAMP=$(date +%Y%m%d%H%M%S)
OUTPUT_DIR="output"
mkdir -p "$OUTPUT_DIR"
cp app/build/outputs/apk/debug/app-debug.apk "${OUTPUT_DIR}/FREEMOTE-DEBUG-${COUNTER}-${TIMESTAMP}.apk" || { echo "Failed to copy APK."; exit 1; }

# Increment counter
echo $((COUNTER + 1)) > build_counter.txt || { echo "Failed to update counter file."; exit 1; }

echo "Built: ${OUTPUT_DIR}/FREEMOTE-DEBUG-${COUNTER}-${TIMESTAMP}.apk"
