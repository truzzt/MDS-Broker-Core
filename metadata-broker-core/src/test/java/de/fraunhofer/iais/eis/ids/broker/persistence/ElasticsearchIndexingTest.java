package de.fraunhofer.iais.eis.ids.broker.persistence;

import de.fraunhofer.iais.eis.*;
import de.fraunhofer.iais.eis.ids.index.common.persistence.ElasticsearchIndexing;
import de.fraunhofer.iais.eis.ids.component.core.util.CalendarUtil;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import de.fraunhofer.iais.eis.util.Util;
import org.apache.http.HttpHost;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.*;
import org.junit.Rule;
import org.junit.runners.MethodSorters;
import org.testcontainers.containers.GenericContainer;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static de.fraunhofer.iais.eis.util.Util.asList;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ElasticsearchIndexingTest {

    private RestHighLevelClient client;
    private ElasticsearchIndexing indexing;

    @Rule
    public GenericContainer elasticsearch = new GenericContainer("elasticsearch:6.5.4")
            .withExposedPorts(9200);

    @Before
    public void setUp() {
        client = new RestHighLevelClient(
                RestClient.builder(new HttpHost("localhost", elasticsearch.getFirstMappedPort(), "http")));
        //Will be automatically filled via dependency management - SPI structure
        //indexing = new ElasticsearchIndexing(client);
    }

    @Test
    @Ignore //TODO
    public void indexingCRUD() throws IOException, DatatypeConfigurationException, URISyntaxException {
        InfrastructureComponent component = createBaseConnectorWithArtifacts();
        addToIndex(component);
        updateIndex(component);
        deleteFromIndex(component);
    }

    private void addToIndex(InfrastructureComponent component) throws IOException {
        indexing.add(component);

        GetResponse response = getFromIndex(component.getId());
        Assert.assertTrue(response.isExists());
    }

    private GetResponse getFromIndex(URI selfDescriptionId) throws IOException {
        GetRequest request = new GetRequest(ElasticsearchIndexing.INDEX_NAME, "doc", selfDescriptionId.toString());
        return client.get(request, RequestOptions.DEFAULT);
    }

    private void updateIndex(InfrastructureComponent component) throws IOException {
        ((BaseConnectorImpl)component).setOutboundModelVersion("2.0.0-modified"); // WARNING this changes the static object, so test order is even more important with this
        indexing.update(component);
        Assert.assertTrue(getFromIndex(component.getId()).getSourceAsString().contains("2.0.0-modified"));
    }

    private void deleteFromIndex(InfrastructureComponent component) throws IOException {
        indexing.delete(component.getId());
        GetResponse response = getFromIndex(component.getId());
        Assert.assertFalse(response.isExists());
    }

    private InfrastructureComponent createBaseConnectorWithArtifacts() throws MalformedURLException, DatatypeConfigurationException, URISyntaxException {
        GregorianCalendar begin = new GregorianCalendar(2014, Calendar.FEBRUARY, 11);
        GregorianCalendar end = new GregorianCalendar(2017, Calendar.MARCH, 6);

        TemporalEntity temporalEntity = new IntervalBuilder()
                ._begin_(new InstantBuilder()._dateTime_(DatatypeFactory.newInstance().newXMLGregorianCalendar(begin)).build())
                ._end_(new InstantBuilder()._dateTime_(DatatypeFactory.newInstance().newXMLGregorianCalendar(end)).build())
                .build();

        Location spatialEntity = new GeoPointBuilder()._latitude_(50.73339f)._longitude_(7.09766f).build();

        Resource demoResource_1 = new DataResourceBuilder()
                ._temporalCoverage_(asList(temporalEntity))
                ._spatialCoverage_(asList(spatialEntity))
                ._description_(asList(new TypedLiteral("demo dataset with some weird content")))
                ._keyword_(asList(new TypedLiteral("Business", "en"), new TypedLiteral("Report@en")))
                ._language_(asList(Language.EN, Language.DE))
                ._title_(asList(new TypedLiteral("demo dataset^^xsd:string")))
                ._standardLicense_(URI.create("https://creativecommons.org/publicdomain/zero/1.0/"))
                ._representation_(asList(new DataRepresentationBuilder()
                        ._instance_(asList(new ArtifactBuilder()
                                ._byteSize_(new BigInteger("100000"))
                                ._fileName_("myfile.txt")
                                ._creationDate_(CalendarUtil.now())
                                .build()))
                        ._mediaType_(new IANAMediaTypeBuilder()._filenameExtension_("TXT").build())
                        .build()))
                .build();

        Resource demoResource_2 = new DataResourceBuilder()
                ._description_(asList(new TypedLiteral("second demo dataset with some weird content")))
                ._keyword_(asList(new TypedLiteral("keyword_1", "en"), new TypedLiteral("keyword_2", "de")))
                ._language_(asList(Language.EN, Language.DE))
                ._title_(asList(new TypedLiteral("second demo dataset")))
                ._standardLicense_(URI.create("https://www.gnu.org/licenses/gpl-3.0.html"))
                ._representation_(asList(new DataRepresentationBuilder()
                        ._instance_(asList(new ArtifactBuilder()
                                ._byteSize_(new BigInteger("100000"))
                                ._fileName_("myfile2.txt")
                                ._creationDate_(CalendarUtil.now())
                                .build()))
                        ._mediaType_(new IANAMediaTypeBuilder()._filenameExtension_("TXT").build())
                        .build()))
                .build();

        ResourceCatalog catalog = new ResourceCatalogBuilder()
                //._offeredResource_(asList(demoResource_1, demoResource_2))
                //._offeredResourceAsResource_(asList(demoResource_1, demoResource_2))
                ._offeredResourceAsObject_(asList(demoResource_1, demoResource_2))
                .build();

        return new BaseConnectorBuilder()
                ._title_(asList(
                        new TypedLiteral("Test Connector"),
                        new TypedLiteral("Test Konnektor", "de"),
                        new TypedLiteral("Test Connector", "en")))
                ._description_(asList(new TypedLiteral("Test Connector Description")))
                ._outboundModelVersion_("2.0.0")
                ._inboundModelVersion_(asList("2.0.0"))
                ._resourceCatalog_(asList(catalog))
                ._curatorAsUri_(new URL("http://example.org/curator").toURI())
                ._maintainerAsUri_(new URL("http://example.org/maintainer").toURI())
                ._securityProfile_(SecurityProfile.BASE_SECURITY_PROFILE)
                .build();
    }
}
