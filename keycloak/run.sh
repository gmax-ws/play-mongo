#!/bin/bash
  
# https://stackoverflow.com/a/47181236/1098564
wget -c https://downloads.jboss.org/keycloak/11.0.3/keycloak-11.0.3.zip
 
#https://askubuntu.com/a/994965/109301
unzip -n keycloak-11.0.3.zip
cd keycloak-11.0.3/bin
./add-user-keycloak.sh -r master -u admin -p password
#./add-user-keycloak.sh -r oauth2-sample -u user1 -p password
./standalone.sh -Djboss.socket.binding.port-offset=10 -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=../../realm-export.json -Dkeycloak.migration.strategy=OVERWRITE_EXISTING -b 0.0.0.0
