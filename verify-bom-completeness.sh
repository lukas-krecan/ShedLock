#!/bin/bash

# BOM Completeness Verification Script
# This script checks that all modules from the parent pom.xml are included in the BOM

set -e

echo "🔍 Verifying BOM completeness..."

# Extract modules from parent pom, excluding test modules and BOM itself
PARENT_MODULES=$(grep -E "<module>" pom.xml | \
    sed -E 's/.*<module>(.*)<\/module>.*/\1/' | \
    grep -v "test/" | \
    grep -v "shedlock-test-support" | \
    grep -v "shedlock-bom" | \
    sed 's|.*/||' | \
    sort)

# Extract artifact IDs from BOM
BOM_ARTIFACTS=$(grep -E "<artifactId>shedlock-" shedlock-bom/pom.xml | \
    sed -E 's/.*<artifactId>(.*)<\/artifactId>.*/\1/' | \
    sort)

echo "📁 Modules in parent pom (excluding tests and support):"
echo "$PARENT_MODULES" | sed 's/^/  /'
echo ""

echo "📦 Artifacts in BOM:"
echo "$BOM_ARTIFACTS" | sed 's/^/  /'
echo ""

# Find missing modules
MISSING=""
for module in $PARENT_MODULES; do
    if ! echo "$BOM_ARTIFACTS" | grep -q "^$module$"; then
        MISSING="$MISSING $module"
    fi
done

# Find extra artifacts
EXTRA=""
for artifact in $BOM_ARTIFACTS; do
    if ! echo "$PARENT_MODULES" | grep -q "^$artifact$"; then
        EXTRA="$EXTRA $artifact"
    fi
done

# Report results
if [ -n "$MISSING" ]; then
    echo "❌ ERROR: The following modules are missing from the BOM:"
    for module in $MISSING; do
        echo "  - $module"
    done
    echo ""
    exit 1
fi

if [ -n "$EXTRA" ]; then
    echo "⚠️  WARNING: The following artifacts in BOM don't correspond to modules:"
    for artifact in $EXTRA; do
        echo "  - $artifact"
    done
    echo ""
fi

echo "✅ BOM verification passed: All modules are properly included in the BOM"
echo ""