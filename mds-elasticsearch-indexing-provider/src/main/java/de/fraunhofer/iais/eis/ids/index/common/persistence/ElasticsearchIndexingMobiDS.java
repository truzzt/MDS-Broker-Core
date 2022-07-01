package de.fraunhofer.iais.eis.ids.index.common.persistence;

import de.fraunhofer.iais.eis.*;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;
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
import java.util.*;
import java.util.stream.Collectors;

public class ElasticsearchIndexingMobiDS extends ElasticsearchIndexing {

    final private Logger logger = LoggerFactory.getLogger(ElasticsearchIndexing.class);
    public static final String RESOURCE_INDEX = "resources"; // previously: "mdmresources"; see also the recreation of the index at Broker-Paris-Core-Container --> metadata-brkoer-core --> SelfDescriptionPersistenceAndIndexing.refreshIndex() where the hard-coded index names are used!
    final private Map<String, String> mdsUriToLabelMap = new HashMap<>();

    /**
     * Constructor
     *
     */
    public ElasticsearchIndexingMobiDS() {
        super();
        createMdsIdToLabelMap();
    }

    private static String getOntologyQuery(URI connectorId, String resourceId, String domainAttr) {
        return  "PREFIX ids: <https://w3id.org/idsa/core/> \n " +
                "PREFIX mds: <http://w3id.org/mds#>  \n" +
                "SELECT (GROUP_CONCAT(DISTINCT ?value;SEPARATOR=\", \") AS ?values) WHERE {\n" +
                "  GRAPH <" + connectorId + "> {" +
                "  <"+resourceId+"> a ids:Resource;\n" +
                "  "+domainAttr+" ?value .\n" +
                "} }";
    }

    private void createMdsIdToLabelMap() {
        // importing the ontology into a rdf model
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("mds/mds-ontology.ttl")), Lang.TTL);

        // Extracting structural mappings out of the ontology
        model.listStatements(null, RDFS.label, (String) null)
                .forEach(statement -> {
                    org.apache.jena.rdf.model.Resource subject = statement.getSubject();
                    RDFNode object = statement.getObject();
                    mdsUriToLabelMap.put(subject.getURI(), object.asLiteral().getString());
                });
    }

    @Override
    public void add(InfrastructureComponent selfDescription) throws IOException {
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
                        try {

                            client.index(resIndexAdd, RequestOptions.DEFAULT);
                            logger.info("Creating resource " + resource.getId().toString() + " which belongs to the connector " + connector.getId().toString());
                        }
                        catch(IOException e) {
                            e.printStackTrace();
                            logger.error("Error while creating resource indices");
                        }
                    }}}
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
                    try {
                        client.delete(resIndexDelete, RequestOptions.DEFAULT);
                        logger.info("Deleting resource " + res.get("resourceID").toString() + "from Resource Index which belongs to the connector "+res.get("connectorID"));
                    } catch (IOException e) {
                        e.printStackTrace();
                        logger.error("Error while deleting resource indices");
                    }
                }

            }
        }
    }


    @Override
    protected void handleResourceCustomFields( Resource resource, XContentBuilder builder, URI connectorId) throws IOException {
        fbw.x(() -> builder.field("resourceAsJsonLd", resource.toRdf()), "resourceAsJsonLd");
        fbw.x(() -> builder.field( "lastChanged", System.currentTimeMillis()),"lastChanged");
      /*  try {
            String originID = resource.getProperties().get("http://www.w3.org/2002/07/owl#sameAs").toString();
            String formattedOriginID = originID.substring(1, originID.length() - 2);
            fbw.x(() -> builder.field("originURI", formattedOriginID), "resourceAsJsonLd");
        }
        catch (Exception e) {
            logger.error("Could not find Property: sameAs ");
        }*/
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

//        if(resource.getDomainVocabulary() != null) {
//            if(resource.getDomainVocabulary().getVocabulary() != null)
//                fbw.x(() -> builder.field("domainVocabulary", resource.getDomainVocabulary().getVocabulary().toString()),"domainVocabulary");
//        }

        //domain specific terms to be fetched from fuseki
        //(Removed:"mobids:roadNetworkCoverage")
        //Added: roadNetworkCoverageDescription,dataFormatAdditionalDescription,dataModel,networkCoverage
        List<String> domainAttrs = readDomainProperties();
                //Arrays.asList("mds:dataFormatAdditionalDescription", "mds:dataModel", "mds:geoReferenceMethod", "mds:transportMode", "mds:dataSubcategory", "mds:dataCategory");
                //Arrays.asList("mobids:transportMode", "mobids:DataCategoryDetail", "mobids:DataCategory", "mobids:geoReferenceMethod" , "mobids:mdmBrokering", "mobids:NutsLocation","mobids:roadNetworkCoverageDescription","mobids:dataFormatAdditionalDescription","mobids:dataModel","mobids:networkCoverage");
                // fill the list with the properties from the model

                domainAttrs.forEach(attr -> {
            try {
                queryAndIndexDomainAttrs(resource, attr, builder, connectorId);
            } catch (IOException e) {
                logger.error("An error during indexing domain specific attributes");
                e.printStackTrace();
            }
        });
    }

    public List<String> readDomainProperties() {
        List<String> domainAttrs = new ArrayList<>();
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("mds/mds-ontology.ttl")), Lang.TTL);
        model.listStatements(null, null, OWL.ObjectProperty)
                .mapWith(Statement::getSubject)
                .forEach(resource -> domainAttrs.add("mds:" + resource.getLocalName()));

        model.listStatements(null, null, OWL.DatatypeProperty)
                .mapWith(Statement::getSubject)
                .forEach(resource -> domainAttrs.add("mds:" + resource.getLocalName()));
        return domainAttrs;
    }



    private void queryAndIndexDomainAttrs( Resource resource, String domainAttr, XContentBuilder builder, URI connectorId) throws IOException {
        // prepare adjusted query
        String query = getOntologyQuery(connectorId,resource.getId().toString(),domainAttr);

        // perform select query
        ArrayList<QuerySolution> tupleQueryResult = repo.selectQuery(query);

        // Analyze the query results regarding the structure of the ontology
        try {
            if (tupleQueryResult != null && !tupleQueryResult.isEmpty()) {
                List<String> extractedValueList = tupleQueryResult.stream().map(tuple -> tuple.getLiteral("?values").toString()).collect(Collectors.toList());
                if (!extractedValueList.isEmpty() && extractedValueList.get(0) != null) {
                    // If the values are URIs, map them to the labels which are connected to these URIs in the mds-ontology.ttl
                    List<String> mappedURIsList = new ArrayList<>();
                    extractedValueList.forEach( value -> {
                        List<String> splittedValues = Arrays.asList(value.split(","));
                        splittedValues.forEach(splittedValue -> {
                            if (!splittedValue.isEmpty()) {
                                splittedValue = splittedValue.strip();
                                // map the uri to the desired labels
                                String label = mdsUriToLabelMap.getOrDefault(splittedValue, splittedValue);
                                if (splittedValue.startsWith("http") && label.contains(splittedValue)) {
                                    logger.info("Missing label for " + splittedValue + ". Using URI instead.");
                                }
                                // adding the labels to the result set
                                mappedURIsList.add(label);
                            }
                        });

                    });
                    // prepare and map the fields to be added to the fuseki
                    fbw.x(() -> builder.field(domainAttr, mappedURIsList), domainAttr);
                }
                else{
                    logger.info("Empty query results for domain Attributes");
                }
            }
            else{
                logger.info("No mobids specific tags available");
            }

        }
        catch(NullPointerException e)
        {
            logger.warn("Could not find domain specific Meta Data");
        }
    }
}
