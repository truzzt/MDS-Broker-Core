package de.fraunhofer.iais.eis.ids.broker.acceptancetest;

import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.InfrastructureComponent;
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ResourceCatalog;
import org.apache.jena.atlas.iterator.Iter;
import org.junit.Assert;
import org.junit.Test;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.logging.Logger;

public class UpdateMessageTest {
    @Test
    public void UpdateMessage() throws IOException {
        Path filePath = Path.of("C:\\Users\\twirtz\\01 - Projekte\\01. - Laufend\\13. - MetaDataBroker\\broker-paris-core-container\\metadata-broker-core\\src\\test\\resources\\msg.txt");

        String content = Files.readString(filePath);


        Connector connector = (Connector) (new Serializer()).deserialize(content, InfrastructureComponent.class);
        Assert.assertEquals(connector.getResourceCatalog().size(),1);
        Assert.assertNotNull(connector.getResourceCatalog());

        Iterator iter = connector.getResourceCatalog().iterator();

        while (iter.hasNext()) {
            ResourceCatalog resourceCatalog = (ResourceCatalog) iter.next();
            Assert.assertTrue(resourceCatalog.getOfferedResourceAsObject().size()>0);

            Iterator var4 = resourceCatalog.getOfferedResourceAsObject().iterator();
            while(var4.hasNext()) {
                Resource resource = (Resource) var4.next();
                System.out.println(resource.getDescription().toString());
            }

        }

    }
}
