#!/bin/bash

if [ -n "$1" ]; then
        VERSION=$1
else
        echo -n "Enter version: "
        read VERSION
fi

##Tag the images

# core
docker tag registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/core registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/core:$VERSION
echo "taged image: registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/core:$VERSION"

# reverseproxy
docker tag registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/reverseproxy registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/reverseproxy:$VERSION
echo "taged image: registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/reverseproxy:$VERSION"
docker tag registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/reverseproxy registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/reverseproxy:$VERSION
echo "taged image: registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/reverseproxy:$VERSION"

# fuseki
docker tag registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/fuseki registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/fuseki:$VERSION
echo "taged image: registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/fuseki:$VERSION"

# paris
docker tag registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/core registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/core:$VERSION


##Push the images

docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/core:$VERSION
docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/reverseproxy:$VERSION
docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/reverseproxy:$VERSION
docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/fuseki:$VERSION
docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/core:$VERSION


docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/core:latest
docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/reverseproxy:latest
docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/reverseproxy:latest
docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/broker/fuseki:latest
docker push registry.gitlab.cc-asp.fraunhofer.de/eis-ids/paris/core:latest
