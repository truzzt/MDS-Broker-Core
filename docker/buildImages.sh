#!/bin/bash

dos2unix metadata-broker-core/* reverseproxy/* fuseki/*

if [ -n "$1" ]; then
        VERSION=$1
else
        echo -n "Enter version: "
        read VERSION
fi

# metadata-broker,
cp ../metadata-broker-core/target/metadata-broker-core-*.jar metadata-broker-core/

docker build metadata-broker-core/ -t mds/broker/core

# fuseki
docker build fuseki/ -t mds/broker/fuseki

# reverseproxy
docker build reverseproxy/ -t mds/broker/reverseproxy


docker tag mds/broker/core:latest mds/broker/core:${VERSION}
docker tag mds/broker/fuseki:latest mds/broker/fuseki:${VERSION}
docker tag mds/broker/reverseproxy:latest mds/broker/reverseproxy:${VERSION}
