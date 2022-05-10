package de.fraunhofer.iais.eis.ids.index.common.persistence;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class IndexRecreator {

    private static final Logger logger = LoggerFactory.getLogger(IndexRecreator.class);

    /**
     * Function for recreating the entire index from the current state of the repository (triple store). This helps keeping database and index in sync
     * @throws IOException if an exception occurs during the dropping or recreation of the index
     */
    public static void recreateIndex(String indexName, RestHighLevelClient client) throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse response;
        try {
            response = client.indices().delete(request, RequestOptions.DEFAULT);
            if (response.isAcknowledged()) {
                logger.info("Index " + indexName + " was dropped");
            } else {
                logger.error("Could not drop index " + indexName);
            }
        }
        catch (ElasticsearchStatusException e)
        {
            logger.warn("Could not recreate the elasticsearch index. In case this warning appears during startup, it can be safely ignored.");
        }
        //Use the default number of shards: 5
        CreateIndexRequest request1 = new CreateIndexRequest(indexName)
                .settings(
                        Settings.builder()
                                .put("index.number_of_shards", 5)
                                .put("index.mapping.total_fields.limit", 2000)
                );
        response = client.indices().create(request1, RequestOptions.DEFAULT);
        if(response.isAcknowledged())
        {
            logger.info("Index recreated");
        }
        else
        {
            logger.error("Failed to recreate index");
        }
    }

}
