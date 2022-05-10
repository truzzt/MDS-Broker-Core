package de.fraunhofer.iais.eis.ids.index.common.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class which could really do with some updates and documentation. TODO
 */
public class FieldBuilderWrapper {

    final private Logger logger = LoggerFactory.getLogger(getClass());

    public void x(Worker worker, String... fieldName) {
        try {
            worker.doSomething();
        }
        catch (Exception e) {
            //TODO: Do some exception handling. This error message seems to be incorrect or at least not precise enough.
            // Could we please catch something more precise than just ANY exception?
            logger.warn("An exception occurred while trying to index the field: "+ ((fieldName != null) ? " '" +fieldName[0]+ "'." : " (?)"), e);
        }
    }
}