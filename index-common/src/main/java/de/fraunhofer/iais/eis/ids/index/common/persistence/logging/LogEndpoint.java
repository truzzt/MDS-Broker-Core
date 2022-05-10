package de.fraunhofer.iais.eis.ids.index.common.persistence.logging;

import de.fraunhofer.iais.eis.QueryMessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * This class provides an endpoint for the frontend to log messages in the core component log files, which may be signed to prove authenticity
 */
@RestController
public class LogEndpoint {

    Logger logger = LoggerFactory.getLogger(LogEndpoint.class);
    @PostMapping("/logging")
    public void writeLog(@RequestBody String messageToLog, @RequestParam(required = false) String severity)
    {
        if(severity != null && !severity.isEmpty()) {
            switch (severity.toLowerCase()) {
                case "debug":
                    logger.debug(messageToLog);
                    break;
                case "info":
                    logger.info(messageToLog);
                    break;
                case "warn":
                    logger.warn(messageToLog);
                    break;
                case "error":
                    logger.error(messageToLog);
                    break;
                default:
                    throw new NoSuchElementException("Severity " + severity + " does not exist. Use debug, info, warn, or error.");
            }
        }
        else
        {
            logger.info(messageToLog);
        }
    }
}
