#!/bin/bash

./keycloak-11.0.3/bin/standalone.sh -Dkeycloak.migration.action=export -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=realm-export.json
