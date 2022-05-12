package de.fraunhofer.iais.eis.ids.index.common.persistence;

import de.fraunhofer.iais.eis.*;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QuerySolution;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class extends the ElasticsearchIndexingConnector functionality by creating a second index for resources only.
 * If both indexes are required, use this class. If only a connector index is required, use ElasticsearchIndexingConnector
 * If only a Participant index is required, use ElasticsearchIndexingParticipant
 */
public class ElasticsearchIndexing extends ElasticsearchIndexingConnector {

    final private Logger
                                   logger = LoggerFactory
            .getLogger(ElasticsearchIndexingConnector.class);
    public        RepositoryFacade repo;
    private List<String> prefixes;
    private List<String> predicates;
    public static final String RESOURCE_INDEX = "resources";

    /**
     * Constructor
     *
     */
    public ElasticsearchIndexing() {
        super();
    }

    public void setDomainVocabulary( List<String> prefixes, List<String> predicates){
        this.predicates = predicates;
        this.prefixes = prefixes;
        logger.info("Custom Properties loaded: " + predicates);
    }

    public void setRepo( RepositoryFacade repo){
        this.repo = repo;
    }


    @Override
    public void add(InfrastructureComponent selfDescription) throws IOException {
        logger.info("Adding " + selfDescription.getId() + " to index.");
        //Default behaviour: Index the connector with its catalog
        super.add(selfDescription);

        //create a new index for mobids resources
        if (selfDescription instanceof Connector) {
            Connector connector = (Connector) selfDescription;
            indexResources(connector);
        }
    }

    @Override
    public void update(InfrastructureComponent selfDescription) throws IOException {
        logger.info("Updating indexing of " + selfDescription.getId() + " .");
        //Update the connector in the registrations index
        super.update(selfDescription);

        //update index for fhg-digital resources
        if (selfDescription instanceof Connector) {
            Connector connector = (Connector) selfDescription;
            //delete respective resources of the connector in RESOURCE_INDEX first
            deleteResourcesFromIndex(connector.getId().toString());

            //index updated resources of the connector
            indexResources(connector);
        }
    }

    @Override
    public void delete(URI componentId) throws IOException{
        logger.info("Removing " + componentId + " from index.");
        //delete respective resources in RESOURCE_INDEX first
        deleteResourcesFromIndex(componentId.toString());

        //Now remove connector from registrations index
        super.delete(componentId);
    }

    protected void indexResources(Connector connector) throws IOException {

        if(connector.getResourceCatalog() != null && !connector.getResourceCatalog().isEmpty()) {
            for (ResourceCatalog resourceCatalog : connector.getResourceCatalog()) {
                if(resourceCatalog.getOfferedResourceAsObject() != null && !resourceCatalog.getOfferedResourceAsObject().isEmpty()) {
                    for (Resource resource : resourceCatalog.getOfferedResourceAsObject()) {
                        XContentBuilder builder = XContentFactory.jsonBuilder();
                        builder.startObject();
                        handleResource(resource, builder, connector.getId());
                        builder.endObject();
                        IndexRequest resIndexAdd = new IndexRequest(RESOURCE_INDEX)
                            .id(resource.getId().toString())
                            .source(builder);

                        client.index(resIndexAdd, RequestOptions.DEFAULT);
                        logger.info("Creating resource " + resource.getId().toString() + " which belongs to the connector " + connector.getId().toString());
                    }
                }
            }
        }
    }

    private void deleteResourcesFromIndex(String connId) throws IOException {
        SearchRequest reqSearch = new SearchRequest(RESOURCE_INDEX);
        SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .size(10000)
                .query(QueryBuilders.matchAllQuery());
        reqSearch.source(searchSource);
        SearchResponse resSearch = client.search(reqSearch, RequestOptions.DEFAULT);

        if(resSearch.getHits().getHits().length != 0){
            for(SearchHit hit: resSearch.getHits().getHits()) {
                HashMap<String,Object> res = (HashMap<String, Object>) hit.getSourceAsMap();
                   if(connId.equals(res.get("connectorID").toString())) {

                    DeleteRequest resIndexDelete = new DeleteRequest(RESOURCE_INDEX)
                            .id( res.get("resourceID").toString());
                                               client.delete(resIndexDelete, RequestOptions.DEFAULT);
                        logger.info("Deleting resource " + res.get("resourceID").toString() + "from Resource Index which belongs to the connector "+res.get("connectorID"));

                }

            }
        }
    }

    private void indexResource(Connector connector, Resource resource) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        handleResource(resource, builder, connector.getId());
        builder.endObject();
        IndexRequest resIndexAdd = new IndexRequest(RESOURCE_INDEX)
                .id(resource.getId().toString())
                .source(builder);

        client.index(resIndexAdd, RequestOptions.DEFAULT);
        logger.info("Creating resource " + resource.getId().toString() + " which belongs to the connector " + connector.getId().toString());
    }

    private void deleteResourceFromIndex(String connId, String resId) throws IOException {
        SearchRequest reqSearch = new SearchRequest(RESOURCE_INDEX);
        SearchSourceBuilder searchSource = new SearchSourceBuilder()
                .size(10000)
                .query(QueryBuilders.idsQuery().addIds(connId, resId));
        reqSearch.source(searchSource);
        SearchResponse resSearch = client.search(reqSearch, RequestOptions.DEFAULT);

        if(resSearch.getHits().getHits().length != 0){
            for(SearchHit hit: resSearch.getHits().getHits()) {
                HashMap<String,Object> res = (HashMap<String, Object>) hit.getSourceAsMap();
                if(connId.equals(res.get("connectorID").toString())) {

                    DeleteRequest resIndexDelete = new DeleteRequest(RESOURCE_INDEX)
                            .id( res.get("resourceID").toString());
                    client.delete(resIndexDelete, RequestOptions.DEFAULT);
                    logger.info("Deleting resource " + res.get("resourceID").toString() + "from Resource Index which belongs to the connector "+res.get("connectorID"));
                }
            }
        }
    }


    @Override
    protected void handleResourceCustomFields(Resource resource, XContentBuilder builder, URI connectorId) throws IOException {
        fbw.x(() -> builder.field("resourceAsJsonLd", resource.toRdf()), "resourceAsJsonLd");
        fbw.x(() -> builder.field( "lastChanged", System.currentTimeMillis()),"lastChanged");
        try {
            String originID = resource.getProperties().get("http://www.w3.org/2002/07/owl#sameAs").toString();
            String formattedOriginID = originID.substring(5, originID.length() - 1);
            fbw.x(() -> builder.field("originURI", formattedOriginID), "resourceAsJsonLd");
        }
        catch (Exception e) {
            logger.error("Could not find Property: sameAs ");
        }

        if(resource.getSovereignAsObject() != null) {
            builder.startObject("sovereignAsObject");
            if (resource.getSovereignAsObject() instanceof Participant) {
                ElasticsearchIndexingParticipant.handleParticipantFields((Participant) resource.getSovereignAsObject(), builder);
            } else {
                ElasticsearchIndexingParticipant.handleAgentFields(resource.getSovereignAsObject(), builder);
            }
            builder.endObject();
        } else if (resource.getSovereignAsUri() != null) {
            fbw.x(() -> builder.field("sovereignAsUri", resource.getSovereignAsUri().toString()), "sovereignAsUri");
        }

        //domain specific terms to be fetched from fuseki

        if ( (! (predicates == null) && !predicates.isEmpty()  && ! (prefixes == null) && !prefixes.isEmpty()))

        {List<String> domainAttrs = predicates;

        String extendedPrefixes = prefixes.stream().map(elem -> "PREFIX "  + elem + " ").collect(Collectors.joining(" \n"));
        domainAttrs.forEach(attr -> {
            try {


                queryAndIndexDomainAttrs(resource, attr, extendedPrefixes, builder);
            } catch (IOException e) {
                logger.error("An error during indexing domain specific attributes");
                e.printStackTrace();
            }
        });
    } else {

      logger.info("No custom fields are configured");
        }
    }

    private void queryAndIndexDomainAttrs(Resource resource, String domainAttr, String extendedPrefixes, XContentBuilder builder) throws IOException {
        String resourceID = resource.getId().toString();
        String connectorID = resource.getId().toString().substring(0,resourceID.indexOf("/", resourceID.lastIndexOf("connectors/")+12)); //TODO: This is exceptionally dangerous! Why not search for the first slash after "/connectors/"?
        ParameterizedSparqlString query = new ParameterizedSparqlString( "PREFIX ids: <https://w3id.org/idsa/core/> \n " +
                extendedPrefixes +
                "SELECT ?value WHERE {\n" +
                "  GRAPH <" + connectorID + "> {" +
                "  ?resource a ids:Resource;\n" +
                "  "+domainAttr+" ?value .\n" +
                "} }");

        query.setIri("resource", resource.getId().toString());
        logger.info(query.toString());
        try {
            ArrayList<QuerySolution> tupleQueryResult = repo.selectQuery(query.toString());
            if (tupleQueryResult != null && !tupleQueryResult.isEmpty()) {
                List<String> someValues = tupleQueryResult.stream().map(tuple -> tuple.getLiteral("?value").toString()).collect(Collectors.toList());
                if (someValues.get(0) != null) {
                    fbw.x(() -> builder.field(domainAttr, someValues), domainAttr);
                }
            }
        }
        catch(NullPointerException ignored)
        {
        }
    }

    @Override
    public void updateResource( Connector reducedConnector, Resource resource )
            throws IOException {
        logger.info("Update indexing of " + reducedConnector.getId() + ".");
        super.update(reducedConnector);
        logger.info("Delete old resources from indexing of " + reducedConnector.getId() + ".");
        deleteResourceFromIndex(reducedConnector.getId().toString(), resource.getId().toString());
        logger.info("Add updated resources to indexing of " + reducedConnector.getId() + ".");
        indexResource(reducedConnector, resource);
    }

    @Override
    public void deleteResource( Connector reducedConnector, URI resourceId )
            throws IOException {
        logger.info("Start deletion of " + resourceId.toString() + " from resource Index.");
        super.delete(resourceId);
        logger.info("Start removing resource " + resourceId.toString() + " from the resource list of the connector " + reducedConnector.getId().toString());
        super.update(reducedConnector);
    }
}
