#!/usr/bin/env bash

# Identity management
export DOCKER_IMAGE=openjdk:8-jre-alpine
export KEYCLOAK_NAME=keycloak
export KEYCLOAK_FROM=$DOCKER_IMAGE
export KEYCLOAK_IMAGE=keycloak:1.0
export KEYCLOAK_VERSION=12.0.4
export KEYCLOAK_HTTP_PORT=8090
export KEYCLOAK_HTTPS_PORT=8453
export KEYCLOAK_REALM=integration
export KEYCLOAK_URI=http://$KEYCLOAK_IP:$KEYCLOAK_HTTP_PORT/auth
#export KEYCLOAK_URI=https://$KEYCLOAK_IP:$KEYCLOAK_HTTPS_PORT/auth
export KEYCLOAK_URL=${KEYCLOAK_URI}/realms/${KEYCLOAK_REALM}/protocol/openid-connect
