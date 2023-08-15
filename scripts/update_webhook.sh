#!/bin/bash

# Check if the yml file is passed as an argument
if [ $# -eq 0 ]; then
  echo "Please provide the path to the YAML file."
  exit 1
fi

# File path
YAML_FILE="$1"

# Ensure yq is installed
if ! command -v yq &> /dev/null; then
  echo "yq is not installed. Please install it first."
  exit 1
fi

# Extract values using yq
TELEGRAM_BOT_TOKEN=$(yq e '.TELEGRAM_BOT_TOKEN' $YAML_FILE)
WEB_HOOK=$(yq e '.WEB_HOOK' $YAML_FILE)

echo "TELEGRAM_BOT_TOKEN: " $TELEGRAM_BOT_TOKEN
echo "WEB_HOOK: " $WEB_HOOK
# Execute curl command
curl -F "url=${WEB_HOOK}" https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook
