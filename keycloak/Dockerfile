FROM openjdk:8-jre-alpine
COPY ./realm-export.json /tmp/realm-export.json
RUN wget -c https://github.com/keycloak/keycloak/releases/download/12.0.4/keycloak-12.0.4.zip
RUN unzip -n keycloak-12.0.4.zip -d /opt
RUN mv /opt/keycloak-12.0.4 /opt/keycloak
ENTRYPOINT /opt/keycloak/bin/standalone.sh -Djboss.socket.binding.port-offset=10 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/tmp/realm-export.json -Dkeycloak.migration.strategy=OVERWRITE_EXISTING -b 0.0.0.0
