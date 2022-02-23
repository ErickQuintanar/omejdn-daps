#!/bin/bash
GR=`tput setaf 2`
NC=`tput sgr0`

echo "${GR}Cleaning up the environment for testing the DAPS${NC}"

# Restore server's certs from backup and cleanup testing keys directory
cd .. && rm -r omejdn-server/keys/ && mv omejdn-server/keys-backup/ omejdn-server/keys/ && rm -r keys/* && \
echo "${GR}Restored server certs and deleted testing keys directory${NC}"

# Restore existing DAPS configuration
echo "---" > config/clients.yml && \
mv omejdn-server/config/clients.yml.orig omejdn-server/config/clients.yml && \
mv omejdn-server/config/omejdn.yml.orig omejdn-server/config/omejdn.yml && \
mv omejdn-server/config/scope_mapping.yml.orig omejdn-server/config/scope_mapping.yml && \
echo "${GR}Restored existing DAPS configuration${NC}"

# Remove configuration file for testing
rm tests/test_config.txt && \
echo "${GR}Deleted configuration file for testing${NC}"