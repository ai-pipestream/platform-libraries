#!/bin/bash

# Script to regenerate gRPC stubs for both Java and Node.js/TypeScript
# Run this after modifying proto files in grpc/grpc-stubs/src/main/proto

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
GRPC_STUBS_DIR="$PROJECT_ROOT/grpc/grpc-stubs"
GRPC_NODE_DIR="$PROJECT_ROOT/grpc/node"

echo "================================================"
echo "Regenerating gRPC Stubs"
echo "================================================"
echo ""

# Step 1: Build grpc-google-descriptor (contains base descriptor sets)
echo "📦 Step 1: Building grpc-google-descriptor..."
cd "$PROJECT_ROOT"

./gradlew :grpc:grpc-google-descriptor:clean :grpc:grpc-google-descriptor:build :grpc:grpc-google-descriptor:publishToMavenLocal

if [ $? -ne 0 ]; then
  echo "❌ Google descriptor build failed!"
  exit 1
fi

echo "✅ Google descriptor built and published to Maven local"
echo ""

# Step 2: Regenerate and build Java stubs
echo "📦 Step 2: Regenerating Java stubs..."
echo "   Location: $GRPC_STUBS_DIR"

./gradlew :grpc:grpc-stubs:clean :grpc:grpc-stubs:build :grpc:grpc-stubs:publishToMavenLocal --rerun-tasks --no-build-cache

if [ $? -ne 0 ]; then
  echo "❌ Java stub generation failed!"
  exit 1
fi

echo "✅ Java stubs regenerated, built, and published to Maven local"
echo ""

# Step 3: Regenerate Node.js/TypeScript stubs
echo "📦 Step 3: Regenerating Node.js/TypeScript stubs..."
echo "   Location: $GRPC_NODE_DIR"
cd "$GRPC_NODE_DIR"

pnpm build

if [ $? -ne 0 ]; then
  echo "❌ Node.js stub generation failed!"
  exit 1
fi

echo "✅ Node.js/TypeScript stubs regenerated successfully"
echo ""

# Step 4: Auto-publish new alpha version
echo "📦 Step 4: Publishing new alpha version to npm..."
cd "$GRPC_NODE_DIR"

# Get current base version and increment alpha
CURRENT_VERSION=$(jq -r '.version' package.json)
BASE_VERSION=$(echo $CURRENT_VERSION | cut -d- -f1)
TIMESTAMP=$(date +%Y%m%d%H%M%S)
NEW_VERSION="${BASE_VERSION}-alpha.${TIMESTAMP}"

echo "   Current: $CURRENT_VERSION"
echo "   New:     $NEW_VERSION"

npm version $NEW_VERSION --no-git-tag-version

if [ $? -ne 0 ]; then
  echo "❌ Version update failed!"
  exit 1
fi

npm publish --tag alpha

if [ $? -ne 0 ]; then
  echo "❌ npm publish failed!"
  exit 1
fi

echo "✅ Published @ai-pipestream/grpc-stubs@${NEW_VERSION}"
echo ""

# Step 5: Summary
echo "================================================"
echo "✨ Stub regeneration complete!"
echo "================================================"
echo ""
echo "Generated and published:"
echo "  Java (Maven local):       $GRPC_STUBS_DIR/build/classes/java/quarkus-generated-sources/grpc/"
echo "  TypeScript (npm):         @ai-pipestream/grpc-stubs@${NEW_VERSION}"
echo ""
echo "Next steps:"
echo "  1. For Java services: Rebuild to pick up updated stubs"
echo "     - cd <service-dir> && ./gradlew clean build"
echo ""
echo "  2. For frontend: Update to new alpha version"
echo "     - cd platform-frontend && ./scripts/sync-grpc-stubs-version.sh ${NEW_VERSION}"
echo "     - cd platform-frontend && pnpm install"
echo "     - cd platform-frontend && ./scripts/nuclear-clean.sh"
echo ""
echo "Or run this command to sync automatically:"
echo "  [ -f ../platform-frontend/scripts/sync-grpc-stubs-version.sh ] && ../platform-frontend/scripts/sync-grpc-stubs-version.sh ${NEW_VERSION}"
echo ""
echo "Published version: ${NEW_VERSION}"
echo ""
