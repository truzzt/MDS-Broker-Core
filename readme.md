# MDS Broker Repository
This repository provides the source code for the extentions of the open-source implementation of the [MetaDataBroker](https://github.com/International-Data-Spaces-Association/metadata-broker-open-core). Its central extension is an implementation of the Indexing-Interface that provide the core functionality for the front-end as well as the relevant https-Endpoints for the front-end.

## Building
### Buildung the Open Source MetaDataBroker
As the MetaDataBroker is an extension to the open source implementation of the MetaDataBroker, you can either build the MetaDataBroker from a particular branch of the open-source broker or query a particular artifact from the Fraunhofer IAIS Artifactory. We start with the former approach an first build the open source broker. First checkout the repository of the open core broker
```sh
git clone https://github.com/International-Data-Spaces-Association/metadata-broker-open-core.git
```
'cd' into the repository and checkout the branch or tag you want to use as a basis for the MetaDataBroker extension of the open source broker. Please have in mind, that the extension was provided with respect to a particular release of the open source broker as well as the [information model](https://github.com/International-Data-Spaces-Association/InformationModel). </br></br> Now that you have chosen your preferred branch or tag of the open source MetaDataBroker you simply build it with maven
```sh
mvn clean package
```
and install it into your repository, e.g. locally using
```sh
mvn install
```
If you do not prefer to build it locally, but in a build pipeline, you will want to push the artifact of the open source broker into an Artifactory. Please adjust the Artifactory configuration in the pom.xml of the *metadata-broker-core*, *broker-common*, *indexing-common*  and the *mds-elasticsearch-indexing-provider* modules accordingly. All of them depend on the  implementation of the open source MetaDataBroker. Under the assumption that this configuration works we proceed.</br></br>

### Buildung the MDS-Extension MetaDataBroker
Now clone **this** repository and repeat the procedure above. Build the MetaDataBroker
```sh
mvn clean package
```
The resulting "metadata-broker-core-*.jar" file in the target folder of the *metadata-broker-core* module is the final artifact of the broker.
## Run the MetaDataBroker in DEBUG Mode
Before you deploy the MetaDataBroker you might want to test the core functionality like persisting and indexing locally. To do so first follow the procedure as explained in sections "Buildung the Open Source MetaDataBroker" and "Buildung the MDS-Extension MetaDataBroker". Now "cd" from the root to the docker and there to the composefiles folder. Enter DEBUG
```sh
cd ./docker/composefiles/DEBUG
```
Start the Container
```sh
docker-compose -up
```
Now open the module *metadata-broker-core* in your favorite IDE (we tested it with Intellij). And run the main in debug mode. It might be that the test fails due to the specified location of the keystore and later due to the validation of the DAPS-certification. To fix the former change the path of the keystore (*ssl.javakeystore*) in your *application.properties* to the exemplary one in the resource folder (*isstbroker-keystore.jks*). Regarding the DAPS-certificate validation: In case that you working in a local setup only and not in a critical infrastructure you can set in your *application.properties* the property *daps.validateIncoming* to *false*. Now runing it should seamlessly work.
### Manual Testing
In order to test the core use-cases of the MetaDataBroker we refer to the provided [Postman Collection](https://github.com/Mobility-Data-Space/MDS-Broker-Core/blob/development/IDS%20Broker%20ES%20Tests%20with%20MDS.postman_collection.json). If you add this collection to you postman workspace you can easily validate the use cases of the broker. By default you find
* the elastic search instance under http://localhost:9200; the registered connectors and resources under http://localhost:9200/registrations and http://localhost:9200/resources/, respectively
## Deployment
The repository comes with scipts to build a default container.
<div class="panel panel-warning">
**Warning**
{: .panel-heading}
<div class="panel-body">

WARNING DESCRIPTION

</div>
</div>
