package de.fraunhofer.iais.eis.ids.index.common.persistence;

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.index.common.persistence.spi.Indexing;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.util.Iterator;
import java.util.stream.Collectors;

/**
 * This class provides an index for a graphical user interface via web access to the indexing service (broker / ParIS)
 */
public class ElasticsearchIndexingConnector implements Indexing<InfrastructureComponent> {

    final static private Logger logger = LoggerFactory.getLogger(ElasticsearchIndexingConnector.class);

    public static final String INDEX_NAME = "registrations";
    public static final String RESOURCE_INDEX_NAME = "resources";

    protected final RestHighLevelClient client;
    protected static final FieldBuilderWrapper fbw = new FieldBuilderWrapper();

    public static String elasticsearchHostname = "localhost"; //default value, to be overwritten by application properties or docker-compose config
    public static int elasticsearchPort = 9200; //Also default value

    private RestHighLevelClient getElasticsearchClient() {
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(elasticsearchHostname, elasticsearchPort, "http")));
    }

    /**
     * Constructor
     */
    public ElasticsearchIndexingConnector() {
        this.client = getElasticsearchClient();
    }


    @Override
    public void addResourceAsJson(String resoruceID, String resourceAsJson) {
        logger.info(String.format("addResourceAsJson (class:%s : not implemented",this.getClass().toString()));
    }

    /**
     * Function for adding an infrastructure component, such as a connector, to the index
     * @param infrastructureComponent The infrastructure component to be indexed
     * @throws IOException if the infrastructure component could not be added, e.g. because it already exists
     */
    @Override
    public void add(InfrastructureComponent infrastructureComponent) throws IOException {
        IndexRequest request = new IndexRequest(INDEX_NAME)
                .id(infrastructureComponent.getId().toString())
                .source(getXContentBuilderForInfrastructureComponent(infrastructureComponent));
        client.index(request, RequestOptions.DEFAULT);
    }

    /**
     * Function for updating an already indexed infrastructure component
     * @param infrastructureComponent The infrastructure component in its current form
     * @throws IOException if the infrastructure component could not be updated, e.g. because it was not found
     */
    @Override
    public void update(InfrastructureComponent infrastructureComponent) throws IOException {
        UpdateRequest updateRequest = new UpdateRequest(INDEX_NAME,  infrastructureComponent.getId().toString())
                .doc(getXContentBuilderForInfrastructureComponent(infrastructureComponent));
        client.update(updateRequest, RequestOptions.DEFAULT);
    }

    @Override
    public void updateResource( Connector reducedConnector, Resource resource )
            throws IOException {
        logger.info("IGNORED indexResource");
    }

//    @Override
//    public void updateResource( URI reducedConnector, Resource resource )
//            throws IOException {
//        logger.info("IGNORED indexResource");
//    }

    /**
     * Function for removing an indexed infrastructure component OR participant from the index
     * @param componentId A reference to the infrastructure component to be removed
     * @throws IOException if the infrastructure component could not be deleted, e.g. because it was not found
     */
    @Override
    public void delete(URI componentId) throws IOException{
        DeleteRequest deleteRequest = new DeleteRequest(INDEX_NAME)
                .id(componentId.toString());
        client.delete(deleteRequest, RequestOptions.DEFAULT);
        deleteRequest = new DeleteRequest(RESOURCE_INDEX_NAME)
                .id(componentId.toString());
        client.delete(deleteRequest, RequestOptions.DEFAULT);
    }

    @Override
    public void deleteResource(Connector reducedConnector, URI resourceId) throws IOException {
        DeleteRequest deleteRequest = new DeleteRequest(RESOURCE_INDEX_NAME)
                .id(resourceId.toString());
        client.delete(deleteRequest, RequestOptions.DEFAULT);
        this.update(reducedConnector);
    }

    /**
     * Function for recreating the entire index from the current state of the repository (triple store). This helps keeping database and index in sync
     * @throws IOException if an exception occurs during the dropping or recreation of the index
     */
    @Override
    public void recreateIndex(String indexName) throws IOException {
        IndexRecreator.recreateIndex(indexName, client);
    }

    /**
     * Utility function for improving readability of security guarantees in security profiles
     * @param securityProfile The security profile to be simplified
     * @return Prettified list of security guarantees
     */
    // PNL-TODO!!!
    protected java.util.List<String> createSecurityProfileNames(SecurityProfile securityProfile) {
        //prevent exception (either UnsupportedOperationException or NullPointerException)
        try {
            securityProfile.getSecurityGuarantee();
        }
        catch (UnsupportedOperationException | NullPointerException ignored)
        {
            return null;
        }
        return securityProfile.getSecurityGuarantee().stream()
                .map(securityGuarantee -> {
                    int index = securityGuarantee.getId().toString().lastIndexOf("/");
                    String name = securityGuarantee.getId().toString().substring(index + 1).toLowerCase();
                    name = name.replaceFirst("securityprofile", "");
                    return name.substring(0, 1).toUpperCase() + name.substring(1);
                })
                .collect(Collectors.toList());
    }

    /**
     * Function to add a resource to an existing XContentBuilder
     * @param resource The resource object which should be attached to the ContentBuilder
     * @param builder An existing XContentBuilder with an open array to which a resource should be attached
     * @param connectorId Embed connector id for every resource
     * @throws IOException thrown if some required values could not be extracted
     */
    protected void handleResource(Resource resource, XContentBuilder builder, URI connectorId) throws IOException {
        //Root object of resource is created by calling method
        //builder.startObject();

        //Handle some basic fields first, such as ID, title, description, language, license, version, and more
        handleResourceBasicFields(resource, builder, connectorId);

        //More complex: Temporal / Spatial coverage of resources
        handleResourceCoverage(resource, builder);

        if(resource.getResourceEndpoint() != null && !resource.getResourceEndpoint().isEmpty())
        {
            builder.startArray("endpoint");
            for (ConnectorEndpoint endpoint : resource.getResourceEndpoint())
            {
                builder.startObject();
                handleConnectorEndpoint(endpoint, builder);
                builder.endObject();
            }
            builder.endArray();
        }

        if (resource.getRepresentation() != null && !resource.getRepresentation().isEmpty()) {
            builder.startArray("representation");
            for (Representation representation : resource.getRepresentation()) {
                builder.startObject();
                handleResourceRepresentation(representation, builder);
                builder.endObject();
            }
            builder.endArray();
        }

        if(resource.getContractOffer() != null && !resource.getContractOffer().isEmpty()) {
            builder.startArray("contract");
            for(ContractOffer contract : resource.getContractOffer()) {
                builder.startObject();
                handleResourceContract(contract, builder);
                builder.endObject();
            }
            builder.endArray();
        }

        //Custom fields for child classes
        handleResourceCustomFields(resource, builder, connectorId);

    }

    /**
     * This function can be overridden by custom implementations to append additional fields to a resource without overriding the entire handleResource function
     * @param resource Resource to be indexed
     * @param builder Builder to which resource custom fields should be added
     */
    protected void handleResourceCustomFields(Resource resource, XContentBuilder builder, URI connectorId) throws IOException {
        //Do nothing here. Only child classes should do something here

    }

    /**
     * Adds all flat fields of a resource to the builder - no sub objects
     * @param resource Resource to be indexed
     * @param builder Builder to which flat resource fields should be added
     */
    protected void handleResourceBasicFields(Resource resource, XContentBuilder builder, URI connectorId) throws IOException {

        fbw.x(() -> builder.field("resourceID", resource.getId().toString()), "resourceID");
        fbw.x(() -> builder.field("connectorID", connectorId.toString()), "connectorID");
        if(resource.getTitle() != null)
//        fbw.x(() -> buildTypedLiterals(builder, "title", resource.getTitle()), "title");
            fbw.x(() -> builder.field("title", resource.getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "title");

        if(resource.getDescription() != null)
//        fbw.x(() -> buildTypedLiterals(builder, "description", resource.getDescription()), "description");
            fbw.x(() -> builder.field("description", resource.getDescription().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "description");

        if(resource.getKeyword() != null)
            fbw.x(() -> builder.field("keyword", resource.getKeyword().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "keywords");

/*
        if(resource.getPaymentModality() != null)
            fbw.x(() -> builder.field("paymentModality", resource.getPaymentModality().toString()), "paymentModality");
*/

        if(resource.getSample() != null)
            if(!resource.getSample().isEmpty())
            fbw.x(() -> builder.field("sample", resource.getSample().get(0).getId().toString()), "sample");



        //fbw.x(() -> builder.field("originalID", getOriginalId(resource)), "originalID");

        if(resource.getLanguage() != null) {
            fbw.x(() -> builder.field("language", resource.getLanguage().stream().map(lang -> lang.getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())).collect(Collectors.toList())), "languages");
            fbw.x(() -> builder.field("labelLanguage", resource.getLanguage().stream().map(lang -> lang.getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())).collect(Collectors.toList())), "labelLanguage");
        }

        if(resource.getCustomLicense() != null) {
            fbw.x(() -> builder.field("customLicense", resource.getCustomLicense().toString()), "custom license");
        }

        if(resource.getStandardLicense() != null)
            fbw.x(() -> builder.field("standardLicense", resource.getStandardLicense().toString()), "standard license");
        //fbw.x(() -> builder.field("labelStandardLicense", resource.getStandardLicense().getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "labelStandardLicense");

        if(resource.getVersion() != null)
            fbw.x(() -> builder.field("version", resource.getVersion()), "version");

        if(resource.getContentType() != null) {
            fbw.x(() -> builder.field("contentType", resource.getContentType().getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "contentType"); //Potentially legacy. The two below are used for FhG Digital
            fbw.x(() -> builder.field("contentTypeSerialized", resource.getContentType().getId().toString()), "contentTypeSerialized");
            fbw.x(() -> builder.field("contentTypeLabel", resource.getContentType().getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "contentTypeLabel");
        }
        if(resource.getContentStandard() != null)
            fbw.x(() -> builder.field("contentStandard", resource.getContentStandard().toString()), "contentStandard");

        if(resource.getPublisherAsObject() != null) {
            builder.startObject("publisherAsObject");
            if (resource.getPublisherAsObject() instanceof Participant) {
                ElasticsearchIndexingParticipant.handleParticipantFields((Participant) resource.getPublisherAsObject(), builder);
            } else {
                ElasticsearchIndexingParticipant.handleAgentFields(resource.getPublisherAsObject(), builder);
            }
            builder.endObject();
        } else if (resource.getPublisherAsUri() != null) {
            fbw.x(() -> builder.field("publisherAsUri", resource.getPublisherAsUri().toString()), "publisherAsUri");
        }
    }

    /**
     * Add temporal and spatial coverage objects - each is handled by a separate subroutine
     * @param resource Resource with potential coverage
     * @param builder Builder to which coverage should be added
     */
    protected void handleResourceCoverage(Resource resource, XContentBuilder builder) throws IOException {
        if (resource.getTemporalCoverage() != null && !resource.getTemporalCoverage().isEmpty()) {
            builder.startArray("temporalCoverages");
            handleTemporalCoverage(resource, builder);
            builder.endArray();
        }

        if (resource.getSpatialCoverage() != null && !resource.getSpatialCoverage().isEmpty()) {
            builder.startArray("spatialCoverages");
            for (Location location : resource.getSpatialCoverage()) {

                handleSpatialCoverage(location, builder);
            }
            builder.endArray();
        }
    }

    /**
     * Handles temporal coverage of a resource and adds it to the index
     * @param resource Resource with temporal coverage
     * @param builder Builder to which temporal coverage should be added
     */
    protected void handleTemporalCoverage(Resource resource, XContentBuilder builder) throws IOException {

        for (TemporalEntity temporalEntity : resource.getTemporalCoverage()) {
            builder.startObject();
            if (temporalEntity instanceof Instant) {
                fbw.x(() -> builder.timeField("temporalCoverage_instant", ((Instant) temporalEntity).getDateTime().toGregorianCalendar().getTime()), "temporalCoverage_instant");
            }
            else if (temporalEntity instanceof Interval) {
                builder.startObject("temporalCoverage_interval");
                if(((Interval) temporalEntity).getBegin() != null)
                    fbw.x(() -> builder.timeField("begin", ((Interval) temporalEntity).getBegin().getDateTime().toGregorianCalendar().getTime()), "begin");
                if(((Interval) temporalEntity).getEnd() != null)
                    fbw.x(() -> builder.timeField("end", ((Interval) temporalEntity).getEnd().getDateTime().toGregorianCalendar().getTime()), "end");
                builder.endObject();
            }
            builder.endObject();
        }
    }

    /**
     * Handles spatial coverage of a resource and adds it to the index
     * @param location Location to save
     * @param builder Builder to which spatial coverage should be added
     */
    protected void handleSpatialCoverage(Location location, XContentBuilder builder) throws IOException {

        builder.startObject();
        if (location instanceof BoundingPolygon) {
            builder.startArray("spatialCoverage_polygon");
            for (GeoPoint geoPoint : ((BoundingPolygon) location).getGeoPoint()) {
                builder.startObject();
                fbw.x(() -> builder.latlon("spatialCoverage_poi", geoPoint.getLatitude(), geoPoint.getLongitude()), "spatialCoverage_poi");
                builder.endObject();
            }
            builder.endArray();
        } else if (location instanceof GeoPointImpl) {
            fbw.x(() -> builder.latlon("spatialCoverage_poi", ((GeoPointImpl) location).getLatitude(), ((GeoPointImpl) location).getLongitude()), "spatialCoverage_poi");
        }
        else if (location instanceof GeoFeatureImpl) {
            fbw.x(() -> builder.field("spatialCoverage_ref", location.getId().toString()));

        }

        builder.endObject();
    }


    /**
     * Handles static endpoints and adds them to the index
     * @param connectorEndpoint Static endpoint to be indexed
     * @param builder Builder to which the endpoint should be added
     */
    protected void handleConnectorEndpoint(ConnectorEndpoint connectorEndpoint, XContentBuilder builder) throws IOException {

        //Handle simple fields: ID, path, inbound path, outbound path
        handleEndpointBasicFields(connectorEndpoint, builder);
        //Handle artifact served by this endpoint
        Artifact connectorEPArtifact = connectorEndpoint.getEndpointArtifact();
        if(connectorEPArtifact != null) {
            builder.startObject("endpointArtifact");
            handleConnectorEndpointArtifact(connectorEPArtifact, builder);
            builder.endObject();
        }
    }

    /**
     * Handles basic flat fields of all endpoint types and adds them to the index
     * @param endpoint Endpoint to be indexed
     * @param builder Builder to which fields should be added
     */
    protected void handleEndpointBasicFields(Endpoint endpoint, XContentBuilder builder) throws IOException {


        builder.field("id", endpoint.getId().toString());
        if(endpoint.getPath() != null)
            fbw.x(() -> builder.field("Path", endpoint.getPath()), "Path");
        if(endpoint.getInboundPath() != null)
            fbw.x(() -> builder.field("inboundPath", endpoint.getInboundPath()), "inboundPath");
        if(endpoint.getOutboundPath() != null)
            fbw.x(() -> builder.field("outboundPath", endpoint.getOutboundPath()), "outboundPath");
        if(endpoint.getEndpointDocumentation() != null)
            fbw.x(() -> builder.field("endpointDocumentation", endpoint.getEndpointDocumentation().toString()), "endpointDocumentation");
        if(endpoint.getEndpointInformation() != null && endpoint.getEndpointInformation().size() > 0)
            fbw.x(() -> builder.field("endpointInformation", endpoint.getEndpointInformation()), "endpointInformation");
        if(endpoint.getAccessURL() != null)
            fbw.x(() -> builder.field("endpointAccessUrl", endpoint.getAccessURL().toString()), "endpointAccessUrl");
    }

    /**
     * Handles the artifact which is offered by a static endpoint and adds it to the index
     * @param artifact Artifact to be indexed
     * @param builder Builder to which the artifact should be added
     */
    protected void handleConnectorEndpointArtifact(Artifact artifact, XContentBuilder builder) throws IOException {
        builder.field("id", artifact.getId().toString());
        if(artifact.getByteSize() != null && !artifact.getByteSize().equals(new BigInteger("0")))
            fbw.x(() -> builder.field("bytesize", artifact.getByteSize()), "bytesize");
        if(artifact.getFileName() != null)
            fbw.x(() -> builder.field("filename", artifact.getFileName()), "filename");
        if(artifact.getCreationDate() != null)
            fbw.x(() -> builder.timeField("creation", artifact.getCreationDate().toGregorianCalendar().getTime()), "creation");
    }

    /**
     * Handles the representation of a resource and adds it to the index
     * @param representation Representation to be indexed
     * @param builder Builder to which the representation should be added
     */
    protected void handleResourceRepresentation(Representation representation, XContentBuilder builder) throws IOException {
        handleRepresentationBasicFields(representation, builder);

        if(representation.getMediaType() != null) {
            handleRepresentationMediaType(representation.getMediaType(), builder);
        }

        builder.startArray("instance");
        for (RepresentationInstance instance : representation.getInstance()) {
            if (instance instanceof Artifact) {
                Artifact artifact = (Artifact) instance;
                builder.startObject();
                handleArtifact(artifact, builder);
                builder.endObject();
            }
        }
        builder.endArray();
    }

    /**
     * Handles basic flat fields of a representation and adds it to the index
     * @param representation Representation to be indexed
     * @param builder Builder to which flat fields of resource should be added
     */
    protected void handleRepresentationBasicFields(Representation representation, XContentBuilder builder) throws IOException {

        if(representation.getId() != null)
            builder.field("id", representation.getId().toString());
        if(representation.getRepresentationStandard() != null)
            fbw.x(() -> builder.field("representationStandard", representation.getRepresentationStandard().toString()), "representationStandard");

    }

    /**
     * Handles the media type of a resource and adds it to the index
     * @param mediaType Media type to be indexed
     * @param builder Builder to which media type should be added
     */
    protected void handleRepresentationMediaType(MediaType mediaType, XContentBuilder builder)
    {
            fbw.x(() -> builder.field("mediatype", mediaType.getId().toString()), "mediatype");
            fbw.x(() -> builder.field("labelMediatype", mediaType.getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "labelMediatype");
            if(mediaType.getFilenameExtension() != null) {
                fbw.x(() -> builder.field("filenameExtensionMediatype", mediaType.getFilenameExtension()), "labelMediatype");
            }
    }

    /**
     * Handles artifacts and adds them to the index
     * @param artifact Artifact to be indexed
     * @param builder Builder to which artifact should be added
     */
    protected void handleArtifact(Artifact artifact, XContentBuilder builder) throws IOException {
        builder.field("id", artifact.getId().toString());

        //fbw.x(() -> builder.field("originalID", getOriginalId(artifact)), "originalID");

        if(artifact.getByteSize() != null && !artifact.getByteSize().equals(new BigInteger("0")))
            fbw.x(() -> builder.field("bytesize", artifact.getByteSize()), "bytesize");
        if(artifact.getFileName() != null)
            fbw.x(() -> builder.field("filename", artifact.getFileName()), "filename");
        if(artifact.getCreationDate() != null)
            fbw.x(() -> builder.timeField("creation", artifact.getCreationDate().toGregorianCalendar().getTime()), "creation");
    }

    /**
     * Handles basic flat fields of a contract attached to a resource and adds it to the index
     * @param contract Contract to be indexed
     * @param builder Builder to which the contract should be added
     */
    protected void handleResourceContractBasicFields(Contract contract, XContentBuilder builder) throws IOException {
        builder.field("id", contract.getId().toString());
        if(contract.getConsumer() != null)
            fbw.x(() -> builder.field("contractConsumer", contract.getConsumer().toString()), "contractConsumer");
        if(contract.getProvider() != null)
            fbw.x(() -> builder.field("contractProvider", contract.getProvider().toString()), "contractProvider");
        if(contract.getContractStart() != null)
            fbw.x(() -> builder.timeField("contractStart", contract.getContractStart().toGregorianCalendar().getTime()), "contractStart");
        if(contract.getContractEnd() != null)
            fbw.x(() -> builder.timeField("contractEnd", contract.getContractEnd().toGregorianCalendar().getTime()), "contractEnd");
        if(contract.getContractDate() != null)
            fbw.x(() -> builder.timeField("contractDate", contract.getContractDate().toGregorianCalendar().getTime()), "contractDate");

    }

    /**
     * Handles the annex of a contract and adds it to the index
     * @param contract Contract with annex to be indexed
     * @param builder Builder to which annex should be added
     */
    protected void handleResourceContractAnnex(Contract contract, XContentBuilder builder)
    {
        if(contract.getContractAnnex().getTitle() != null)
            fbw.x(() -> builder.field("annexTitle", contract.getContractAnnex().getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "annexTitle");
    }


    /**
     * Handles contract document attached to a resource and adds it to the index
     * @param contract Contract to be indexed
     * @param builder Builder to which contract should be added
     */
    protected void handleResourceContractDocument(Contract contract, XContentBuilder builder) throws IOException {
        if(contract.getContractDocument().getId() != null)
            builder.field("id", contract.getContractDocument().getId().toString());
        if(contract.getContractDocument().getTitle() != null)
            fbw.x(() -> builder.field("docTitle", contract.getContractDocument().getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "docTitle");
        if(contract.getContractDocument().getDescription() != null)
            fbw.x(() -> builder.field("docDesc", contract.getContractDocument().getDescription().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "docDesc");

    }

    /**
     * Handles the duty of a contract and adds it to the index
     * @param duty Duty to be indexed
     * @param builder Builder to which duty should be added
     */
    protected void handleResourceContractPermissionDuty(Duty duty, XContentBuilder builder) throws IOException {
        handleResourceContractDutyBasicFields(duty, builder);

        if(duty.getAssignee() != null) {
            fbw.x(() -> builder.field("dutyAssignee", duty.getAssignee().stream().map(URI::toString).collect(Collectors.toList())), "dutyAssignee");
        }
        if(duty.getAssigner() != null) {
            fbw.x(() -> builder.field("dutyAssigner", duty.getAssigner().stream().map(URI::toString).collect(Collectors.toList())), "dutyAssigner");
        }
        if(duty.getConstraint() != null) {
            builder.startArray("dutyConstraint");
            for (AbstractConstraint c : duty.getConstraint()) {
                builder.startObject();
                handleAbstractConstraint(c, builder);
                builder.endObject();
            }
            builder.endArray();
        }
        //TODO: more fields to build?
    }

    /**
     * Handles a general contract constraint and adds it to the index
     * @param constraint Constraint to be indexed
     * @param builder Builder to which constraint should be added
     */
    static void handleAbstractConstraint(AbstractConstraint constraint, XContentBuilder builder) throws IOException {
        if (constraint instanceof Constraint) {
            if (((Constraint) constraint).getLeftOperand() != null)
                fbw.x(() -> builder.field("leftOperand", ((Constraint) constraint).getLeftOperand().getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "leftOperand");
            if (((Constraint) constraint).getOperator() != null)
                fbw.x(() -> builder.field("operator", ((Constraint) constraint).getOperator().getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "operator");
            if (((Constraint) constraint).getRightOperand() != null)
                fbw.x(() -> builder.field("rightOperand", ((Constraint) constraint).getRightOperand().getValue()), "rightOperand");
        }
        else if(constraint instanceof LogicalConstraint)
        {
            if(((LogicalConstraint) constraint).getAnd() != null)
            {
                builder.startArray("and");
                for (Constraint c : ((LogicalConstraint) constraint).getAnd()) {
                    builder.startObject();
                    handleAbstractConstraint(c, builder);
                    builder.endObject();
                }
            }
            if(((LogicalConstraint) constraint).getOr() != null)
            {
                builder.startArray("or");
                for (Constraint c : ((LogicalConstraint) constraint).getOr()) {
                    builder.startObject();
                    handleAbstractConstraint(c, builder);
                    builder.endObject();
                }
            }
            if(((LogicalConstraint) constraint).getXone() != null)
            {
                builder.startArray("xone");
                for (Constraint c : ((LogicalConstraint) constraint).getXone()) {
                    builder.startObject();
                    handleAbstractConstraint(c, builder);
                    builder.endObject();
                }
            }
        }
        else
        {
            logger.warn("Provided abstractConstraint is neither constraint nor logicalConstraint: " + constraint.toRdf());
        }
    }

    /**
     * Handles the basic flat fields of a contract duty and adds it to the index
     * @param duty Duty to be indexed
     * @param builder Builder to which flat fields of duty should be added
     */
    protected void handleResourceContractDutyBasicFields(Duty duty, XContentBuilder builder) throws IOException {
        builder.field("id", duty.getId().toString());
        if(duty.getTitle() != null)
            fbw.x(() -> builder.field("dutyTitle", duty.getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "dutyTitle");
        if(duty.getDescription() != null)
            fbw.x(() -> builder.field("dutyDesc", duty.getDescription().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "dutyDesc");
        if(duty.getAction() != null)
            fbw.x(() -> builder.field("dutyAction", duty.getAction().stream().map(action-> action.getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())).collect(Collectors.toList())), "dutyAction");

    }

    /**
     * Handles the permission of a contract and adds it to the index
     * @param permission Permission to be indexed
     * @param builder Builder to which permission should be added
     */
    protected void handleResourceContractPermission(Permission permission, XContentBuilder builder) throws IOException {
        handleResourceContractPermissionBasicFields(permission, builder);

        if(permission.getAssignee() != null) {
            fbw.x(() -> builder.field("permissionAssignee", permission.getAssigner().stream().map(URI::toString).collect(Collectors.toList())), "permissionAssignee");
        }
        if(permission.getAssigner() != null) {
            fbw.x(() -> builder.field("permissionAssigner", permission.getAssigner().stream().map(URI::toString).collect(Collectors.toList())), "permissionAssigner");
        }

        if(permission.getConstraint() != null) {
            builder.startArray("permissionConstraint");
            for (AbstractConstraint c : permission.getConstraint()) {
                builder.startObject();
                handleAbstractConstraint(c, builder); //Same as for duty/prohibition
                builder.endObject();
            }
            builder.endArray();
        }

        if(permission.getPreDuty() != null && !permission.getPreDuty().isEmpty())
        {
            builder.startArray("permissionPreDuty");
            for(Duty duty : permission.getPreDuty()) {
                builder.startObject();
                handleResourceContractPermissionDuty(duty, builder);
                builder.endObject();
            }
            builder.endArray();
        }

        if(permission.getPostDuty() != null && !permission.getPostDuty().isEmpty())
        {
            builder.startArray("permissionPostDuty");
            for(Duty duty : permission.getPostDuty()) {
                builder.startObject();
                handleResourceContractPermissionDuty(duty, builder);
                builder.endObject();
            }
            builder.endArray();
        }

        //TODO: more fields to build?
    }

    /**
     * Handles the basic flat fields of a contract permission and adds it to the index
     * @param permission Permission to be indexed
     * @param builder Builder to which flat fields of permission should be added
     */
    protected void handleResourceContractPermissionBasicFields(Permission permission, XContentBuilder builder) throws IOException {
        builder.field("id", permission.getId().toString());
        if(permission.getTitle() != null)
            fbw.x(() -> builder.field("permissionTitle", permission.getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "permissionTitle");
        if(permission.getDescription() != null)
            fbw.x(() -> builder.field("permissionDesc", permission.getDescription().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "permissionDesc");
        if(permission.getAction() != null)
            fbw.x(() -> builder.field("permissionAction", permission.getAction().stream().map(action-> action.getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())).collect(Collectors.toList())), "permissionAction");

    }

    /**
     * Handles the prohibition of a contract and adds it to the index
     * @param prohibition Prohibition to be indexed
     * @param builder Builder to which prohibition should be added
     */
    protected void handleResourceContractProhibition(Prohibition prohibition, XContentBuilder builder) throws IOException {
        handleResourceContractProhibitionBasicFields(prohibition, builder);

        if(prohibition.getConstraint() != null) {
            builder.startArray("prohibitionConstraint");
            for (AbstractConstraint c : prohibition.getConstraint()) {
                builder.startObject();
                handleAbstractConstraint(c, builder); //Same as for Duty and Permission
                builder.endObject();
            }
            builder.endArray();
        }
        if(prohibition.getAssignee() != null) {
            fbw.x(() -> builder.field("prohibitionAssignee", prohibition.getAssignee().stream().map(URI::toString).collect(Collectors.toList())), "prohibitionAssignee");
        }

        if(prohibition.getAssigner() != null) {
            fbw.x(() -> builder.field("prohibitionAssigner", prohibition.getAssignee().stream().map(URI::toString).collect(Collectors.toList())), "prohibitionAssigner");
        }

        //TODO: more fields to build?
    }

    /**
     * Handles the basic flat fields of a contract prohibition and adds it to the index
     * @param prohibition Prohibition to be indexed
     * @param builder Builder to which flat fields of prohibition should be added
     */
    protected void handleResourceContractProhibitionBasicFields(Prohibition prohibition, XContentBuilder builder) throws IOException {
        builder.field("id", prohibition.getId().toString());
        if(prohibition.getTitle() != null)
            fbw.x(() -> builder.field("prohibitionTitle", prohibition.getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "prohibitionTitle");
        if(prohibition.getDescription() != null)
            fbw.x(() -> builder.field("prohibitionDesc", prohibition.getDescription().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "prohibitionDesc");
        if(prohibition.getAction() != null)
            fbw.x(() -> builder.field("prohibitionAction", prohibition.getAction().stream().map(action-> action.getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())).collect(Collectors.toList())), "prohibitionAction");
    }

    /**
     * Handles the contract of a resource via a number of subroutines and adds it to the index
     * @param contract Contract to be indexed
     * @param builder Builder to which the contract should be added
     */
    protected void handleResourceContract(Contract contract, XContentBuilder builder) throws IOException {
        handleResourceContractBasicFields(contract, builder);

        if(contract.getContractAnnex() != null) {
            builder.startObject("contractAnnex");
            handleResourceContractAnnex(contract, builder);
            builder.endObject();
        }

        if(contract.getContractDocument() != null) {
            builder.startObject("contractDocument");
            handleResourceContractDocument(contract, builder);
            builder.endObject();
        }

        if(contract.getPermission() != null) {
            builder.startArray("contractPermission");
            for(Permission permission : contract.getPermission()) {
                builder.startObject();
                handleResourceContractPermission(permission, builder);
                builder.endObject();
            }
            builder.endArray();
        }

        if(contract.getProhibition() != null) {
            builder.startArray("contractProhibition");
            for(Prohibition prohibition : contract.getProhibition()) {
                builder.startObject();
                handleResourceContractProhibition(prohibition, builder);
                builder.endObject();
            }
            builder.endArray();
        }

    }



    /**
     * Function to create an XContentBuilder describing an infrastructure component
     * @param infrastructureComponent The infrastructure component which should be described by the ContentBuilder
     * @return Returns an XContentBuilder describing the passed infrastructure component
     * @throws IOException thrown if some required values could not be extracted
     */
    protected XContentBuilder getXContentBuilderForInfrastructureComponent(InfrastructureComponent infrastructureComponent) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();

        //Create root object
        builder.startObject();

        // technical connector details
        builder.startObject("connector");
        handleConnectorBasicFields(infrastructureComponent, builder);
        builder.endObject();

        // provider details of a connector
        builder.startObject("provider");
        handleConnectorProviderDetails(infrastructureComponent, builder);
        builder.endObject();

        // resources offered by connector
        if(infrastructureComponent instanceof Connector) {
            if(((Connector) infrastructureComponent).getResourceCatalog() != null && !((Connector) infrastructureComponent).getResourceCatalog().isEmpty()) {
                builder.startArray("catalog");
                for (ResourceCatalog catalog : ((Connector) infrastructureComponent).getResourceCatalog()) {
                    builder.startObject();
                    handleConnectorCatalog(catalog, builder, infrastructureComponent.getId());
                    builder.endObject();
                }
                builder.endArray();
            }
        }
        //Close root object
        builder.endObject();

        return builder;
    }

    /**
     * Handles the basic flat fields of a connector and adds it to the index
     * @param infrastructureComponent Infrastructure Component / Connector to be indexed
     * @param builder Builder to which the Connector should be added
     */
    protected void handleConnectorBasicFields(InfrastructureComponent infrastructureComponent, XContentBuilder builder)
    {


        //basic fields
        if(infrastructureComponent.getTitle() != null)
            fbw.x(() -> builder.field("title", infrastructureComponent.getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "title");
        if(infrastructureComponent.getDescription() != null)
            fbw.x(() -> builder.field("description", infrastructureComponent.getDescription().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "description");
        if(infrastructureComponent.getInboundModelVersion() != null)
            fbw.x(() -> builder.field("inboundModelVersions", infrastructureComponent.getInboundModelVersion()), "inboundModelVersions");
        if(infrastructureComponent.getOutboundModelVersion() != null)
            fbw.x(() -> builder.field("outboundModelVersion", infrastructureComponent.getOutboundModelVersion()), "outboundModelVersion");
        if(infrastructureComponent.getVersion() != null)
            fbw.x(() -> builder.field("connectorVersion", infrastructureComponent.getVersion()), "connectorVersion");
        if(infrastructureComponent.getPhysicalLocation() != null)
        {
            fbw.x(() -> {
                        builder.startArray("connectorLocation");
                        handleSpatialCoverage(infrastructureComponent.getPhysicalLocation(), builder); // TODO only handle the first entry for now
                        builder.endArray();
                    }
            );
        }
        try {
            String originID = infrastructureComponent.getProperties().get("http://www.w3.org/2002/07/owl#sameAs").toString();
            fbw.x(() -> builder.field( "lastChanged", System.currentTimeMillis()),"lastChanged");
            String formattedOriginID = originID.substring(5, originID.length() - 1);
            fbw.x(() -> builder.field("originURI", formattedOriginID), "resourceAsJsonLd");

        }
        catch (Exception e) {
            logger.error("Could not find Property: sameAs ");
        }
        try {
            if (infrastructureComponent instanceof Connector) {
                if (((Connector) infrastructureComponent).getHasDefaultEndpoint() != null)
                    fbw.x(() -> builder.field("accessUrl", ((Connector) infrastructureComponent).getHasDefaultEndpoint().getAccessURL().toString()), "accessUrl");
            }
        }
        catch(Exception e){
            logger.error("Could not find Connector Endpoint AccessUrl");
        }
        //security profile is a more complex, handle in separate, overridable function
        handleSecurityProfile(infrastructureComponent, builder);
    }

    /**
     * Handles the security profile object of a connector
     * @param infrastructureComponent Connector to be indexed
     * @param builder Builder to which security profile should be added
     */
    protected void handleSecurityProfile(InfrastructureComponent infrastructureComponent, XContentBuilder builder)
    {

        if (infrastructureComponent instanceof Connector && ((Connector) infrastructureComponent).getSecurityProfile() != null) {
            if(createSecurityProfileNames(((Connector) infrastructureComponent).getSecurityProfile()) == null)
            {
                fbw.x(() -> builder.field("securityProfile", ((Connector) infrastructureComponent).getSecurityProfile().getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "securityProfile");
            }
            else {
                fbw.x(() -> builder.field("securityProfile", createSecurityProfileNames(((Connector) infrastructureComponent).getSecurityProfile())), "securityProfile");
            }
        }
    }

    /**
     * Handles the provider details of a connector and adds them to the index
     * @param infrastructureComponent Connector to be indexed
     * @param builder Builder to which provider details should be added
     */
    protected void handleConnectorProviderDetails(InfrastructureComponent infrastructureComponent, XContentBuilder builder) throws IOException {
        // TODO: Find out why setting the other variant to empty is necessary for maintainer and curator
        //       but not for sovereign and publisher
        if(infrastructureComponent.getMaintainerAsObject() != null) {
            builder.startObject("maintainerAsObject");
            ElasticsearchIndexingParticipant.handleParticipantFields(infrastructureComponent.getMaintainerAsObject(), builder);
            builder.endObject();
            builder.nullField("maintainerAsUri");
        } else if (infrastructureComponent.getMaintainerAsUri() != null) {
            fbw.x(() -> builder.field("maintainerAsUri", infrastructureComponent.getMaintainerAsUri().toString()), "maintainerAsUri");
            builder.nullField("maintainerAsObject");
        }

        if(infrastructureComponent.getCuratorAsObject() != null) {
            builder.startObject("curatorAsObject");
            ElasticsearchIndexingParticipant.handleParticipantFields(infrastructureComponent.getCuratorAsObject(), builder);
            builder.endObject();
            builder.nullField("curatorAsUri");
        } else if (infrastructureComponent.getCuratorAsUri() != null) {
            fbw.x(() -> builder.field("curatorAsUri", infrastructureComponent.getCuratorAsUri().toString()), "curatorAsUri");
            builder.nullField("curatorAsObject");
        }
    }

    //Possible TODO: We only handle offers. In case that we want to handle requests as well, we should take care of that here
    protected void handleConnectorCatalog(ResourceCatalog catalog, XContentBuilder builder, URI connectorId) throws IOException {
        if(catalog.getOfferedResourceAsObject() != null) {
            builder.startArray("resources");
            for (Resource resource : catalog.getOfferedResourceAsObject()) {
                builder.startObject();
                fbw.x(() -> builder.field("resourceID", resource.getId().toString()), "resourceID");

                if(resource.getTitle() != null)
                    fbw.x(() -> builder.field("title", resource.getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "title");

                if(resource.getDescription() != null)
                    fbw.x(() -> builder.field("description", resource.getDescription().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "description");

                if(resource.getKeyword() != null)
                    fbw.x(() -> builder.field("keyword", resource.getKeyword().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "keywords");

                if(resource.getPublisherAsUri() != null)
                fbw.x(() -> builder.field("publisherAsUri", resource.getPublisherAsUri().toString()), "publisherAsUri");

                builder.endObject();
            }
            for (URI resourceUri : catalog.getOfferedResourceAsUri()) {
                builder.startObject();
                fbw.x(() -> builder.field("resourceID", resourceUri.toString()), "resourceID");
                builder.endObject();
            }
            builder.endArray();
        }
    }

    public String getOriginalId(Resource resource) {

        Iterator<String> iter = resource.getProperties().keySet().iterator();

        while (iter.hasNext()) {
            String property = iter.next();
            if (property.toLowerCase().contains("sameas")) {
                return resource.getProperties().get(property).toString();
            }
        }

        return null;
    }

}
