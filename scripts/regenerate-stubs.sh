#!/bin/bash

# Script to regenerate gRPC stubs for both Java and Node.js/TypeScript
# Run this after modifying proto files in grpc/grpc-stubs/src/main/proto
#
# Usage: ./regenerate-stubs.sh [--npm-publish]
#   --npm-publish    Also publish to npm (normally done via CI/CD)

set -e

# Parse arguments
NPM_PUBLISH=false
while [[ $# -gt 0 ]]; do
  case $1 in
    --npm-publish)
      NPM_PUBLISH=true
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [--npm-publish]"
      echo "  --npm-publish    Also publish to npm (normally done via CI/CD)"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: $0 [--npm-publish]"
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
GRPC_STUBS_DIR="$PROJECT_ROOT/grpc/grpc-stubs"
GRPC_NODE_DIR="$PROJECT_ROOT/grpc/node"
WORKSPACE_ROOT="$PROJECT_ROOT/../platform-frontend"

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

if [ -d "$WORKSPACE_ROOT" ]; then
  cd "$WORKSPACE_ROOT"
  pnpm --filter @ai-pipestream/grpc-stubs build
else
  # Fallback if workspace not found
  cd "$GRPC_NODE_DIR"
  pnpm build
fi

if [ $? -ne 0 ]; then
  echo "❌ Node.js stub generation failed!"
  exit 1
fi

echo "✅ Node.js/TypeScript stubs regenerated successfully"
echo ""

# Step 4: Auto-publish new alpha version (optional)
if [ "$NPM_PUBLISH" = true ]; then
  echo "📦 Step 4: Publishing new alpha version to npm..."
  cd "$GRPC_NODE_DIR"

  # Get current base version and increment alpha
  CURRENT_VERSION=$(jq -r '.version' package.json)
  BASE_VERSION=$(echo $CURRENT_VERSION | cut -d- -f1)
  TIMESTAMP=$(date +%Y%m%d%H%M%S)
  NEW_VERSION="${BASE_VERSION}-alpha.${TIMESTAMP}"

  echo "   Current: $CURRENT_VERSION"
  echo "   New:     $NEW_VERSION"

  # Update version in package.json
  jq --arg v "$NEW_VERSION" '.version = $v' package.json > package.json.tmp && mv package.json.tmp package.json

  if [ $? -ne 0 ]; then
    echo "❌ Version update failed!"
    exit 1
  fi

  # Publish from workspace root
  if [ -d "$WORKSPACE_ROOT" ]; then
    cd "$WORKSPACE_ROOT"
    pnpm --filter @ai-pipestream/grpc-stubs publish --tag alpha --no-git-checks
  else
    pnpm publish --tag alpha --no-git-checks
  fi

  if [ $? -ne 0 ]; then
    echo "❌ pnpm publish failed!"
    exit 1
  fi

  echo "✅ Published @ai-pipestream/grpc-stubs@${NEW_VERSION}"
  echo ""
else
  echo "📦 Step 4: Skipping npm publish (use --npm-publish to publish)"
  echo ""
fi

# Step 5: Summary
echo "================================================"
echo "✨ Stub regeneration complete!"
echo "================================================"
echo ""
echo "Generated:"
echo "  Java (Maven local):       $GRPC_STUBS_DIR/build/classes/java/quarkus-generated-sources/grpc/"
echo "  TypeScript:               $GRPC_NODE_DIR/src/generated/"
echo ""
if [ "$NPM_PUBLISH" = true ]; then
  echo "Published to npm:           @ai-pipestream/grpc-stubs@${NEW_VERSION}"
  echo ""
  echo "Next steps:"
  echo "  1. For Java services: Rebuild to pick up updated stubs"
  echo "     - cd <service-dir> && ./gradlew clean build"
  echo ""
  echo "  2. For frontend: Update to new alpha version"
  echo "     - cd platform-frontend && ./scripts/sync-grpc-stubs-version.sh ${NEW_VERSION}"
  echo "     - cd platform-frontend && pnpm install"
  echo ""
else
  echo "Next steps:"
  echo "  1. For Java services: Rebuild to pick up updated stubs"
  echo "     - cd <service-dir> && ./gradlew clean build"
  echo ""
  echo "  2. npm publish is handled by CI/CD"
  echo ""
fi
