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
import java.net.URI;
import java.util.Collection;
import java.util.stream.Collectors;

import static de.fraunhofer.iais.eis.ids.index.common.persistence.ElasticsearchIndexingConnector.handleAbstractConstraint;

public class ElasticsearchIndexingParticipant implements Indexing<Participant> {

    final static private Logger logger = LoggerFactory.getLogger(ElasticsearchIndexingConnector.class);

    public static final String INDEX_NAME = "registrations";
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
    public ElasticsearchIndexingParticipant() {
        this.client = getElasticsearchClient();
    }


    @Override
    public void addResourceAsJson(String resoruceID, String resourceAsJson) {
        logger.info(String.format("addResourceAsJson (class:%s : not implemented",this.getClass().toString()));
    }

    /**
     * Function for adding a participant to the index
     * @param p The participant to be indexed
     * @throws IOException if the participant could not be added, e.g. because it already exists
     */
    @Override
    public void add(Participant p) throws IOException
    {
        IndexRequest request = new IndexRequest(INDEX_NAME)
                .id(p.getId().toString())
                .source(getXContentBuilderForParticipant(p, XContentFactory.jsonBuilder()));
        client.index(request, RequestOptions.DEFAULT);
    }

    /**
     * Function for updating an already indexed participant
     * @param p The participant in its current form
     * @throws IOException if the participant could not be updated, e.g. because it was not found
     */
    @Override
    public void update(Participant p) throws IOException
    {
        UpdateRequest updateRequest = new UpdateRequest(INDEX_NAME, p.getId().toString())
                .doc(getXContentBuilderForParticipant(p, XContentFactory.jsonBuilder()));
        client.update(updateRequest, RequestOptions.DEFAULT);
    }

    @Override
    public void updateResource( Connector reducedConnector, Resource resource ) {
        logger.info("IGNORED indexResource");
    }

//    @Override
//    public void updateResource(URI reducedConnector, Resource resource) throws IOException {
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
    }

    @Override
    public void deleteResource(Connector reducedConnector, URI resourceId) throws IOException {
        logger.info("IGNORED deleteResource");
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
     * Function to add a participant to an XContentBuilder
     * @param participant The participant object which should be attached to the ContentBuilder
     * @param builder An existing XContentBuilder with an open array to which a participant should be attached
     * @return Returns the same ContentBuilder that was passed to it, with an additional participant attached to it
     * @throws IOException thrown if some required values could not be extracted
     */
    static XContentBuilder getXContentBuilderForParticipant(Participant participant, XContentBuilder builder) throws IOException {
        builder.startObject();

        // participant
        builder.startObject("participant");

        handleParticipantFields(participant, builder);

        builder.endObject();

        builder.endObject();
        return builder;
    }

    static void handleParticipantFields(Participant participant, XContentBuilder builder) throws IOException {
        handleParticipantBasicFields(participant, builder);
        //Leads to NullPointerException when passing null values to buildPlainLiterals

        if(participant.getMemberPerson() != null)
        {
            builder.startArray("memberPerson");
            for(Person person : participant.getMemberPerson()) {
                builder.startObject();
                handleParticipantMemberPerson(person, builder);
                builder.endObject();
            }
            builder.endArray();
        }

        if(participant.getParticipantCertification() != null)
        {
            builder.startObject("certification");
            handleParticipantCertification(participant.getParticipantCertification(), builder);
            builder.endObject();
        }

        participant.getParticipantRefinement();

        if(participant.getBusinessIdentifier() != null && !participant.getBusinessIdentifier().isEmpty())
        {
            builder.startArray("businessIdentifier");
            for(BusinessIdentifier businessIdentifier : participant.getBusinessIdentifier())
            {
                builder.startObject();
                handleParticipantBusinessIdentifier(businessIdentifier, builder);
                builder.endObject();
            }
            builder.endArray();
        }

        if(participant.getParticipantRefinement() != null)
        {
            builder.startObject("participantRefinement");
            handleAbstractConstraint(participant.getParticipantRefinement(), builder);
            builder.endObject();
        }
    }

    static void handleAgentFields(Agent agent, XContentBuilder builder) {
        if(agent.getTitle() != null)
            fbw.x(() -> builder.field("title", agent.getTitle().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "title");

        if(agent.getDescription() != null)
            fbw.x(() -> builder.field("description", agent.getDescription().stream().map(TypedLiteral::getValue).collect(Collectors.toList())), "description");
    }

    /**
     * Adds all flat fields of a participant to the builder - no sub objects
     * @param participant Participant to be indexed
     * @param builder Builder to which flat participant fields should be added
     */
    static void handleParticipantBasicFields(Participant participant, XContentBuilder builder) {

        handleAgentFields(participant, builder);

        if(participant.getCorporateEmailAddress() != null && !participant.getCorporateEmailAddress().isEmpty())
        {
            fbw.x(() -> builder.field("corporateEmailAddress", participant.getCorporateEmailAddress()),"corporateEmailAddress");
        }

        if(participant.getCorporateHomepage() != null)
        {
            fbw.x(() -> builder.field("corporateHomepage", participant.getCorporateHomepage().toString()),"corporateHomepage");
        }
        if(participant.getMemberParticipant() != null && !participant.getMemberParticipant().isEmpty())
        {
            //would be sufficient to provide the URI of the member participant
            fbw.x(() -> builder.field("memberParticipant", participant.getMemberParticipant().stream().map(val -> val.getId().toString()).collect(Collectors.toList())),"memberParticipant");
        }
        if(participant.getVersion() != null)
        {
            fbw.x(() -> builder.field("version", participant.getVersion()),"version");
        }
        if(participant.getPrimarySite() != null)
        {
            fbw.x(() -> builder.field("primarySite", participant.getPrimarySite().getSiteAddress()),"primarySite");
        }

        if(participant.getJurisdiction() != null)
        {
            fbw.x(() -> builder.field("jurisdiction", participant.getJurisdiction()),"jurisdiction");
        }

        if(participant.getLegalForm() != null)
        {
            fbw.x(() -> builder.field("legalForm", participant.getLegalForm()),"legalForm");
        }

        if(participant.getLegalName() != null && !participant.getLegalName().isEmpty())
        {
            fbw.x(() -> builder.field("legalName", participant.getLegalName()),"legalName");
        }

        if(participant.getVatID() != null)
        {
            fbw.x(() -> builder.field("VatID", participant.getVatID()),"VatID");
        }
    }

    /**
     * Handle the Member object of a participant and add it to the index
     * @param person Person which is member of a participant
     * @param builder Builder to which the member person should be added
     */
    static void handleParticipantMemberPerson(Person person, XContentBuilder builder) {

        if(person.getEmailAddress() != null)
            fbw.x(() -> builder.field("emailAddress", person.getEmailAddress()), "emailAddress");
        if(person.getFamilyName() != null)
            fbw.x(() -> builder.field("familyName", person.getFamilyName()), "familyName");
        if(person.getGivenName() != null)
            fbw.x(() -> builder.field("givenName", person.getGivenName()), "givenName");
        if(person.getHomepage() != null)
            fbw.x(() -> builder.field("homepage", person.getHomepage()), "homepage");
        if(person.getPhoneNumber() != null)
            fbw.x(() -> builder.field("phoneNumber", person.getPhoneNumber()), "phoneNumber");
    }

    /**
     * Handle the certification object of a participant and add it to the index
     * @param participantCertification Certification of the participant
     * @param builder Builder to which the certification should be added
     */
    static void handleParticipantCertification(ParticipantCertification participantCertification, XContentBuilder builder)
    {
        if(participantCertification.getMembershipEnd() != null)
            fbw.x(() -> builder.timeField("membershipEnd", participantCertification.getMembershipEnd().toGregorianCalendar().getTime()), "membershipEnd");
        if(participantCertification.getCertificationLevel() != null) {
            fbw.x(() -> builder.field("certificationLevel", participantCertification.getCertificationLevel().getId().toString()),"certificationLevel");
            fbw.x(() -> builder.field("labelCertificationLevel", participantCertification.getCertificationLevel().getLabel().stream().map(TypedLiteral::getValue).collect(Collectors.toList())),"labelCertificationLevel");
        }
        if(participantCertification.getVersion() != null)
            fbw.x(() -> builder.field("version", participantCertification.getVersion()),"version");
        if(participantCertification.getTitle() != null)
            fbw.x(() -> buildTypedLiterals(builder, "title", participantCertification.getTitle()), "title");
        if(participantCertification.getDescription() != null)
            fbw.x(() -> buildTypedLiterals(builder, "description", participantCertification.getDescription()), "description");
        if(participantCertification.getLastValidDate() != null)
            fbw.x(() -> builder.timeField("lastValidDate", participantCertification.getLastValidDate().toGregorianCalendar().getTime()), "lastValidDate");
        if(participantCertification.getEvaluationFacility() != null)
            fbw.x(() -> builder.field("evaluationFacility", participantCertification.getEvaluationFacility().getId().toString()),"evaluationFacility");
    }

    /**
     * Handle the business identifier object of a participant and add it to the index
     * @param businessIdentifier business identifier of the participant
     * @param builder Builder to which the business identifier should be added
     */
    static void handleParticipantBusinessIdentifier(BusinessIdentifier businessIdentifier, XContentBuilder builder)
    {
        if(businessIdentifier.getIdentifierNumber() != null)
        {
            fbw.x(() -> builder.field("identifierNumber", businessIdentifier.getIdentifierNumber()),"identifierNumber");
        }
        if(businessIdentifier.getIdentifierSystem() != null)
        {
            fbw.x(() -> builder.field("identifierSystem", businessIdentifier.getIdentifierSystem()),"identifierSystem");
        }
    }

    /**
     * Utility function to process a typed literal (e.g. a string with language tag, an integer or similar)
     * @param builder The current XContentBuilder to which a field should be attached
     * @param fieldName The name of the field that should be added
     * @param typedLiterals The typed literal from which the values should be extracted
     */
    static void buildTypedLiterals(XContentBuilder builder, String fieldName, Collection<? extends TypedLiteral> typedLiterals) {
        for (TypedLiteral literal : typedLiterals) {
            try {
                if (literal.getLanguage() == null || literal.getLanguage().isEmpty()) {
                    builder.field(fieldName, literal.getValue());
                }
                else {
                    builder.field(fieldName +"_"+ literal.getLanguage(), literal.getValue());
                }
            }
            catch (IOException e) {
                logger.warn("Error indexing literal '" +literal.getValue()+ "'");
            }
        }
    }

}
