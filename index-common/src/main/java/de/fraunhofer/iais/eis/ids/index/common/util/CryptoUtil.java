package de.fraunhofer.iais.eis.ids.index.common.util;


import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

/**
 * Utility class to make retrieving public or private keys from a key store easier
 */
public class CryptoUtil {

    /**
     * This function retrieves a private key from a given key store
     * @param keyStoreStream The key store in the form of an input stream
     * @param keyStorePassword The password of the keystore as String
     * @param keyStoreAlias The key store alias
     * @return Returns the private key
     */
    public static PrivateKey getPrivateKeyFromKeyStore(InputStream keyStoreStream, String keyStorePassword, String keyStoreAlias) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(keyStoreStream, keyStorePassword.toCharArray());
        return (PrivateKey) keyStore.getKey(keyStoreAlias, keyStorePassword.toCharArray());

    }

    /**
     * This function computes the public key from a given private key
     * @param privateKey The private key for which the public key should be retrieved
     * @return The corresponding public key is returned
     */
    public static PublicKey getPublicKeyFromPrivateKey(PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        RSAPrivateCrtKey privk = (RSAPrivateCrtKey) privateKey;
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privk.getModulus(), privk.getPublicExponent());
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(publicKeySpec);
    }
}
