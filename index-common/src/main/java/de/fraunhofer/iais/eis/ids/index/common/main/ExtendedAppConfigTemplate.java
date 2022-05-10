package de.fraunhofer.iais.eis.ids.index.common.main;
/*
import de.fraunhofer.iais.eis.ids.component.core.SelfDescriptionProvider;
import de.fraunhofer.iais.eis.ids.index.common.persistence.ElasticsearchIndexing;
import de.fraunhofer.iais.eis.ids.index.common.persistence.ElasticsearchIndexingResources;
import de.fraunhofer.iais.eis.ids.index.common.persistence.RepositoryFacade;
import org.elasticsearch.client.RestHighLevelClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;


public abstract class ExtendedAppConfigTemplate extends AppConfigTemplate {
    private List<String> prefixes;
    private List<String> predicates;

    public ExtendedAppConfigTemplate(SelfDescriptionProvider selfDescriptionProvider, List<String> prefixes, List<String> predicates) {
        super(selfDescriptionProvider);
        this.prefixes = prefixes;
        this.predicates = predicates;
    }

    public ExtendedAppConfigTemplate(SelfDescriptionProvider selfDescriptionProvider){
        super(selfDescriptionProvider);
        List<String> emptyList = Collections.emptyList();
        this.prefixes = emptyList;
        this.predicates = emptyList;
    }

    public ExtendedAppConfigTemplate elasticSearchClient(RestHighLevelClient elasticSearchClient) {
        RepositoryFacade repositoryFacade = new RepositoryFacade(sparqlEndpointUrl);
        indexing = new ElasticsearchIndexingResources(elasticSearchClient, repositoryFacade, prefixes, predicates);
        return this;
    }

    public ExtendedAppConfigTemplate sparqlEndpointUrl(String sparqlEndpointUrl) {
        this.sparqlEndpointUrl = sparqlEndpointUrl;
        return this;
    }

}
*/