package de.fraunhofer.iais.eis.ids.index.common.persistence.logging;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

//TODO: upon shutting down, one last call should be done towards the signature process. Otherwise the last file before shutdown will never be signed

/**
 * This is an extension of the logback RollingFileAppender, which additionally signs the resulting log file upon rollover, if no external modifications were detected
 * To detect modifications, the VerifyingRollingFileAppender remembers the SHA1 fingerprint of the log file after each append step
 * Before appending, it verifies that the current SHA1 fingerprint matches with the stored one
 */
public class VerifyingRollingFileAppender extends RollingFileAppender<ILoggingEvent> {
    private byte[] digest = null;
    private String fileToSign = null;
    private boolean manipulated = false;
    private final Logger logger = LoggerFactory.getLogger(VerifyingRollingFileAppender.class);
    private final ArrayList<String> messagesToLog = new ArrayList<>();
    private static Key privateKey;
    private static Key publicKey;

    //Private key for creating file signature
    public static void setPrivateKey(Key key)
    {
        privateKey = key;
    }
    //Corresponding public key for validating file signature
    public static void setPublicKey(Key key)
    {
        publicKey = key;
    }

    /**
     * Function to validate authenticity and integrity of a signed log file
     * @param path Path of log file to be validated
     * @return true, if file signature is good, false otherwise
     */
    public static boolean verifyIntegrityOfLogFile(String path)
    {
        try {
            //Change file ending from log file to "signature file"
            String signatureFile = path.replace(".log", ".sgn").replace(".txt", ".sgn");
            //Does signature file exist?
            if(!new File(signatureFile).exists()) return false;
            //Keys are of RSA nature
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, publicKey);
            byte[] signature = Files.readAllBytes(Paths.get(signatureFile));
            byte[] decryptedHash = cipher.doFinal(signature);
            //Does the SHA-256 hash of the current file match with the decrypted hash?
            return Arrays.equals(getSha256(path), decryptedHash);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | IOException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Function to perform the signing of a log file after the program is done with writing to this file
     * @param file path of log file to be signed
     */
    private void signFile(String file) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException {
        String signatureFile = file.replace(".log", ".sgn").replace(".txt", ".sgn");
        if(!new File(signatureFile).exists())
        {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            byte[] signature = cipher.doFinal(getSha256(fileToSign));
            Files.write(Paths.get(signatureFile), signature);
            //System.out.println("Log file integrity is good: " + verifyIntegrityOfLogFile(fileToSign));
        }
        else
        {
            messagesToLog.add("Signature file " + signatureFile + " already exists!");
        }
    }

    /**
     * An override of the default rollover behaviour. When rolling over takes place, check the integrity of the file and sign it
     */
    @Override
    public void rollover() {
        //Perform one last check whether file has been manipulated
        try {
            if(!Arrays.equals(digest, getSha1(fileToSign)))
            {
                manipulated = true;
            }
        }
        catch (IOException e)
        {
            manipulated = true;
            messagesToLog.add("Could not verify integrity of log file during rollover process. Will not sign log file " + fileToSign);
        }
        if(!manipulated)
        {
            //sign the file
            try {
                signFile(fileToSign);
            } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
                e.printStackTrace();
                messagesToLog.add("Failed to create signature file for log " + fileToSign);
            }
        }

        //Do the actual rollover
        super.rollover();

        //We are now starting with a fresh log file. Reset parameters to default
        manipulated = false;
        digest = null;
        //Once rolled over, print messages that occurred during rollover process. This prevents this function from being re-triggered indefinitely (StackOverflow error)
        for(String message : messagesToLog)
        {
            //TODO: In which file should errors which occur during the signing process show up? In the log file that should be signed? Currently it is in the "next file"
            logger.error(message);
        }
        messagesToLog.clear();
        fileToSign = null;
    }

    /**
     * Utility function to get the SHA-256 fingerprint of a file
     * @param fileName path of file
     * @return SHA-256 fingerprint as byte array
     * @throws IOException if file could not be opened
     */
    private static byte[] getSha256(String fileName) throws IOException
    {
        try {
            return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get(fileName)));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Utility function to get the SHA-1 fingerprint of a file. Note that computing the SHA-1 fingerprint of a file is faster than computing the SHA-256 fingerprint
     * @param fileName path of file
     * @return SHA-1 fingerprint as byte array
     * @throws IOException if file could not be opened
     */
    private static byte[] getSha1(String fileName) throws IOException
    {
        return DigestUtils.sha1(new FileInputStream(fileName));
    }

    /**
     * Override the appending functionality to also do an integrity check every time something is appended to the log
     * @param eventObject event to be logged
     */
    @Override
    protected void append(ILoggingEvent eventObject) {

        //Just storing the current file to a variable. This is required during rollover process where this file needs to be signed
        //Asking for the current file during the rollover would already give us the "next file", as the "getFile" method queries the Rollover strategy
        if(fileToSign == null)
        {
            fileToSign = getFile();
        }
        //digest will be null, if this is the first time we write to the file. TODO: We should check here that the file is empty
        if(digest != null)
        {
            try {
                if (!Arrays.equals(digest, getSha1(getFile())))
                {
                    manipulated = true;
                    messagesToLog.add("Unexpected message digest! Log file " + getFile() + " appears to have been manipulated!");
                }
            }
            catch (IOException e)
            {
                messagesToLog.add("IOException occurred while attempting to verify log integrity. Cannot sign log file [" + getFile() + "].");
                e.printStackTrace();
                manipulated = true;
            }
            //Compute hash of file and compare it to stored hash. If this doesn't match, throw error and prevent log signing
        }
        //Append messages that occurred, but do this in a manner that this current function is not called again (would cause StackOverflow error)
        for(String message : messagesToLog)
        {
            LoggingEvent event = new LoggingEvent();
            event.setLevel(Level.WARN);
            event.setLoggerName(this.getClass().getName());
            event.setThreadName(Thread.currentThread().getName());
            event.setMessage(message);
            //Append to file
            super.append(event);
        }
        String fileBeforeAppend = getFile();
        super.append(eventObject);
        //Did a rollover occur? If yes, we must skip one round of computing the SHA (empty)
        if(getFile().equals(fileBeforeAppend)) {
            try {
                digest = getSha1(getFile());
            } catch (IOException e) {
                messagesToLog.add("Could not verify integrity of log file due to an IOException. Will not sign log file " + getFile());
                manipulated = true;
            }
        }

    }
}
