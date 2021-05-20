#!/bin/bash

source ./global.sh

name=$KEYCLOAK_NAME
from=$KEYCLOAK_FROM
version=$KEYCLOAK_VERSION

file="Dockerfile"
docker=true

function makeDockerFile() {
  echo "FROM $from" >"$1"
  {
    echo "COPY ./realm-export.json /tmp/realm-export.json"
    echo "RUN wget -c https://github.com/keycloak/keycloak/releases/download/$version/keycloak-$version.zip"
    echo "RUN unzip -n keycloak-$version.zip -d /opt"
    echo "RUN mv /opt/keycloak-$version /opt/keycloak"
#    echo "RUN /opt/keycloak/bin/add-user-keycloak.sh -r master -u admin -p admin"
    echo "ENTRYPOINT /opt/keycloak/bin/standalone.sh -Djboss.socket.binding.port-offset=10 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/realm-export.json -Dkeycloak.migration.strategy=OVERWRITE_EXISTING -b 0.0.0.0"
  } >>"$1"
}

echo "***** Deploying $name *****"
if $docker; then
  eval "makeDockerFile $file"
  docker rm -f "$name"
  docker-compose up -d --build "$name"
else
  evl "./run.sh"
fi
