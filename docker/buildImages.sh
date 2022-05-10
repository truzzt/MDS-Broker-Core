#!/bin/bash

dos2unix metadata-broker-core/* reverseproxy/* fuseki/*

if [ -n "$1" ]; then
        VERSION=$1
else
        echo -n "Enter version: "
        read VERSION
fi

# GENERIC IMAGES

# metadata-broker, fhg-digital-broker & paris
mvn -f ../ clean package -Drevision=$VERSION
cp ../metadata-broker-core/target/metadata-broker-core-*.jar metadata-broker-core/


docker build metadata-broker-core/ -t registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/core



#cleanup

#legacy
rm -rf ../common/target

rm -rf ../index-common/target
rm -rf ../broker-common/target


# fuseki
docker build fuseki/ -t registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/fuseki

# reverseproxy
docker build reverseproxy/ -t registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/reverseproxy
