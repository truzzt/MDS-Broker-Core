#!/bin/sh

echo "Starting Spring boot app"

echo "Assign JAVA_HOME path from environment" 
#JAVA_HOME=$(find / -name jre)


#JAVA_HOME=$(/usr/lib/jvm/java-11-openjdk-amd64)
#JAVAHOME =${JAVA_HOME}
#cd /path/to/jre/bin
echo --- Copying cacerts and daps to opt/docker/etc 
cp /usr/lib/jvm/java-11-openjdk-amd64/lib/security/cacerts /opt/docker/etc/cacerts
cp daps.crt /opt/docker/etc/
CACERTS=/opt/docker/etc/cacerts

#CACERTS=${JAVAHOME}/lib/security/cacerts

KEYSTORE_PASS=changeit

DAPS_CERT=daps.crt 

ARGS="-Djava.security.egd=file:/dev/./urandom -Dsparql.url=${SPARQL_ENDPOINT}  -Delasticsearch.hostname=${ELASTICSEARCH_HOSTNAME} -Djavax.net.ssl.keyStorePassword=${KEYSTORE_PASS} -Ddaps.certifcatePath=${DAPS_CERT}"


#if [ -e "$CACERTS" ]
#then
echo --- Adding certs to $CACERTS


#./keytool -genkey -alias [mypassword] -keyalg [RSA]
#./keytool -keystore -alias [mypassword] -keyalg [RSA]

  
#$KEYTOOL -import -trustcacerts -noprompt -keystore $CACERTS -storepass ${KEYSTORE_PASS} -trustcacerts -importcert -alias aisec_daps -file $DAPS_CERT
keytool -keystore "${CACERTS}" -storepass ${KEYSTORE_PASS} -noprompt -trustcacerts -importcert -alias aisec_daps -file ${DAPS_CERT}

# Add proxy args
if [ ! -z "$PROXY_HOST" ]; then
    ARGS="${ARGS} -Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT-3128}"
    if [ ! -z "$PROXY_USER" ]; then
        ARGS="${ARGS} -Dhttp.proxyUser=${PROXY_USER}"
    fi
    if [ ! -z "$PROXY_PASS" ]; then
        ARGS="${ARGS} -Dhttp.proxyPassword=${PROXY_PASS}"
    fi
fi

# DAPS token validation
if [ ! -z "$DAPS_VALIDATE_INCOMING" ]; then
    ARGS="${ARGS} -Ddaps.validateIncoming=${DAPS_VALIDATE_INCOMING}"
fi
#JAVAKESTORE
if [ ! -z "$IDENTITY_JAVAKEYSTORE" ]; then
    ARGS="${ARGS} -Dssl.javakeystore=${IDENTITY_JAVAKEYSTORE}"
fi

# MOBIDS
if [ ! -z "$MOBIDS_INDEXING" ]; then
    ARGS="${ARGS} -Dcomponent.create_mdm_resource_index_mobids=${MOBIDS_INDEXING}"
fi

# validate shacl shapes
if [ ! -z "$SHACL_VALIDATION" ]; then
    ARGS="${ARGS} -Dinfomodel.validateWithShacl=${SHACL_VALIDATION}"
fi

# URI of own connector
if [ ! -z "$COMPONENT_URI" ]; then
    ARGS="${ARGS} -Dcomponent.uri=${COMPONENT_URI}"
fi

# URI of own catalog
if [ ! -z "$COMPONENT_CATALOG_URI" ]; then
    ARGS="${ARGS} -Dcomponent.catalogUri=${COMPONENT_CATALOG_URI}"
fi


# Enable debugging
ARGS="${ARGS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -Dlog4j2.formatMsgNoLookups=true"

echo "ARGS=${ARGS}"

exec java ${ARGS} -jar /metadata-broker-core.jar