package de.fraunhofer.iais.eis.ids.index.common.main;

import de.fraunhofer.iais.eis.ids.index.common.persistence.logging.VerifyingRollingFileAppender;
import de.fraunhofer.iais.eis.ids.index.common.util.CryptoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;

/**
 * Template class for Main class of an index service.
 * This template should be used to reduce code redundancy and to improve maintainability of various index services
 */
@Configuration
@EnableAutoConfiguration(exclude = SolrAutoConfiguration.class)
@ComponentScan(basePackages = { "de.fraunhofer.iais.eis.ids.component.protocol.http.server", "de.fraunhofer.iais.eis.ids.broker"} )
public abstract class ExtendedMainTemplate extends MainTemplate {

    /**
     * Initializes verifying rolling file appender logger, which provides integrity protected, authentic logging functionality
     */

    @Autowired
    private Environment env;



    /*public ExtendedMainTemplate(){
        super(javakeystore);
    }*/


    public void initLogVerifier() throws UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, InvalidKeySpecException, FileNotFoundException {
        //PrivateKey privateKey = CryptoUtil.getPrivateKeyFromKeyStore(javakeystore, keystorePassword, keystoreAlias);
 //       PublicKey publicKey = CryptoUtil.getPublicKeyFromPrivateKey(privateKey);
        javakeystore = new FileInputStream(new File(env.getProperty("ssl.javakeystore")));
        PrivateKey privateKey = CryptoUtil.getPrivateKeyFromKeyStore((javakeystore), keystorePassword, keystoreAlias);
        PublicKey publicKey = CryptoUtil.getPublicKeyFromPrivateKey(privateKey);
        VerifyingRollingFileAppender.setPublicKey(publicKey);
        VerifyingRollingFileAppender.setPrivateKey(privateKey);
        javakeystore.close();
        javakeystore = new FileInputStream(new File(env.getProperty("ssl.javakeystore")));
    }
}
