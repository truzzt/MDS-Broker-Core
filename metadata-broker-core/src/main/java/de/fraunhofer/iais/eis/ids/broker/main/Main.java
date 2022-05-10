package de.fraunhofer.iais.eis.ids.broker.main;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import de.fraunhofer.iais.eis.ids.index.common.main.ExtendedMainTemplate;
import de.fraunhofer.iais.eis.ids.index.common.persistence.ElasticsearchIndexing;
import de.fraunhofer.iais.eis.ids.index.common.persistence.ElasticsearchIndexingMobiDS;
import de.fraunhofer.iais.eis.ids.index.common.persistence.logging.VerifyingRollingFileAppender;
import de.fraunhofer.iais.eis.ids.component.core.InfomodelFormalException;
import de.fraunhofer.iais.eis.ids.component.protocol.http.server.ComponentInteractorProvider;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

/**
 * Entry point to the Broker
 */
@Configuration
@EnableAutoConfiguration(exclude = SolrAutoConfiguration.class)
@ComponentScan(basePackages = { "de.fraunhofer.iais.eis.ids.component.protocol.http.server", "de.fraunhofer.iais.eis.ids.broker", "de.fraunhofer.iais.eis.ids.index.common.persistence.logging", "de.fraunhofer.iais.eis.ids.index.common.endpoint" } )
public class Main extends ExtendedMainTemplate implements ComponentInteractorProvider {

    Logger logger = LoggerFactory.getLogger(Main.class);

    //Initializing properties which are not inherited from MainTemplate
    @Value("${sparql.url}")
    private String sparqlEndpointUrl;

    @Value("${infomodel.contextUrl}")
    private String contextDocumentUrl;

    @Value("${jwks.trustedHosts}")
    private Collection<String> trustedJwksHosts;

    @Value("${daps.validateIncoming}")
    private boolean dapsValidateIncoming;

    @Value("${component.create_mdm_resource_index_mobids}")
    private boolean enableMobidsBrokerIndexing;

    //Initializing properties which are not inherited from MainTemplate
    @Value( "${index.maxNumberOfIndexedConnectorResources}" )
    private int maxNumberOfIndexedConnectorResources = 10;

    @Value("${component.responseSenderAgent}")
    private String responseSenderAgent;

    @Value("${infomodel.validateWithShacl}")
    private boolean validateShacl;

    @Value("${elasticsearch.domainVocabularyPrefixes}")
    private List<String> prefixes;

    @Value("${elasticsearch.domainVocabularyPredicates}")
    private List<String> predicates;

    @Value("${ssl.javakeystore}")
    public String javaKeystorePath;

    private RestHighLevelClient elasticsearchClient;

    //Environment allows us to access application.properties
    @Autowired
    private Environment env;


    private ElasticsearchIndexing indexingParameter;

    ElasticsearchIndexingMobiDS elasticsearchIndexingMobiDS ;
    ElasticsearchIndexing elasticsearchIndexing;


    /**
     * This function is called during startup and takes care of the initialization
     */
    @PostConstruct
    @Override
    public void setUp() {
        //Assigning variables which are inherited from MainTemplate
        componentUri = env.getProperty("component.uri");
        componentMaintainer = env.getProperty("component.maintainer");
        componentCatalogUri = env.getProperty("component.catalogUri");
        componentModelVersion = env.getProperty("component.modelversion");
        sslCertificatePath = env.getProperty("ssl.certificatePath");

        ElasticsearchIndexing.elasticsearchHostname = env.getProperty("elasticsearch.hostname");
        ElasticsearchIndexing.elasticsearchPort = Integer.parseInt(Objects.requireNonNull(env.getProperty("elasticsearch.port")));
        refreshAtBeginning = Boolean.parseBoolean(env.getProperty("index.refreshAtBeginning"));
        refreshHours = Integer.parseInt(env.getProperty("index.refreshHours"));
        prefixes = Arrays.asList(env.getProperty("elasticsearch.domainVocabularyPrefixes").split(","));
        predicates = Arrays.asList(env.getProperty("elasticsearch.domainVocabularyPredicates").split(","));

        keystorePassword = env.getProperty("keystore.password");
        keystoreAlias = env.getProperty("keystore.alias");
        //componentIdsId = env.getProperty("component.idsid");
        dapsUrl = env.getProperty("daps.url");
		sparqlEndpointUrl = env.getProperty("sparql.url");
        //javakeystore = env.getProperty("ssl.javakeystore");
        trustAllCerts = Boolean.parseBoolean(env.getProperty("ssl.trustAllCerts"));
        ignoreHostName = Boolean.parseBoolean(env.getProperty("ssl.ignoreHostName"));

        try {
            javakeystore = new FileInputStream(new File(javaKeystorePath));
            logger.info("Found KeyStore at {}.", javaKeystorePath);
        } catch (FileNotFoundException e) {
            logger.warn("Could not find a KeyStore at {}.", javaKeystorePath);
        }

        try {
            try {
                initLogVerifier();
                if(enableMobidsBrokerIndexing){
                    elasticsearchIndexingMobiDS  = new ElasticsearchIndexingMobiDS();
                    indexingParameter = elasticsearchIndexingMobiDS;
                    logger.info("MobiDs Indexing enabled");
                }
                else{
                    elasticsearchIndexing  = new ElasticsearchIndexing();
                    indexingParameter = elasticsearchIndexing;
                    logger.info("Non-Mobids Indexing disabled");
                }

            }
            catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | InvalidKeySpecException e)
            {
                logger.error("Failed to load private key from key store. Will not be able to provide signatures for log files!", e);
            }
            //This is not needed anymore, as the Elasticsearch Client is created during runtime, if dependency is available (Service Loader)
            //elasticsearchClient = createElasticsearchClient();
            multipartComponentInteractor = new AppConfig(createSelfDescriptionProvider(), prefixes, predicates)
                    //.elasticSearchClient(elasticsearchClient)
                    .sparqlEndpointUrl(sparqlEndpointUrl)
                    .contextDocumentUrl(contextDocumentUrl)
                    .catalogUri(new URI(componentCatalogUri))
                    .securityTokenProvider(createSecurityTokenProvider())
                    .trustedJwksHosts(trustedJwksHosts)
                    .dapsValidateIncoming(dapsValidateIncoming)
                    .responseSenderAgent(new URI(responseSenderAgent))
                    .performShaclValidation(validateShacl)
                    .setIndexing(indexingParameter, maxNumberOfIndexedConnectorResources,refreshAtBeginning, refreshHours) //overwrite default service loader behaviour
                    .build();
        }
        catch (URISyntaxException e) {
            throw new InfomodelFormalException(e);
        }
    }

    /**
     * This function should take care of a clean shut down. TODO: Make sure it does... The function is actually not called before shutdown from what I can tell...
     * @throws IOException thrown if the log file could not be signed properly
     */
    @PreDestroy
    @Override
    public void shutDown() throws IOException {
        //TODO: Make this stuff work...
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Map<String, Appender<ILoggingEvent>> appenderMap = new HashMap<>();
        for(ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList())
        {
            Iterator<Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
            while (appenderIterator.hasNext()) {
                Appender<ILoggingEvent> appender = appenderIterator.next();
                if (!appenderMap.containsKey(appender.getName())) {
                    appenderMap.put(appender.getName(), appender);
                }
            }
        }
        if(appenderMap.containsKey(VerifyingRollingFileAppender.class.getName()))
        {
            logger.info("Found appender!");
        }
        else
        {
            logger.info("Did not find appender");
        }
        for(String key : appenderMap.keySet())
        {
            logger.info("Found key " + key);
        }

        elasticsearchClient.close();
    }


    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
