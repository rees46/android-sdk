#!/bin/bash

NEW_VERSION=$1

CURRENT_CODE=$(grep 'VERSION_CODE=' version.properties | cut -d'=' -f2)
NEW_VERSION_CODE=$((CURRENT_CODE + 1))

VERSION_NAME="$NEW_VERSION"

echo "VERSION_CODE=$NEW_VERSION_CODE" > version.properties
echo "VERSION_NAME=$VERSION_NAME" >> version.properties

echo "Updated version to $VERSION_NAME ($NEW_VERSION_CODE)"
