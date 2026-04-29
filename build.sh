#!/bin/bash

# Read current counter
COUNTER=$(cat build_counter.txt)

# Build the APK
./gradlew clean assembleDebug

# Copy and rename with counter
cp app/build/outputs/apk/debug/app-debug.apk "FREEMOTE-DEBUG-${COUNTER}.apk"

# Increment counter
echo $((COUNTER + 1)) > build_counter.txt

echo "Built: FREEMOTE-DEBUG-${COUNTER}.apk"
