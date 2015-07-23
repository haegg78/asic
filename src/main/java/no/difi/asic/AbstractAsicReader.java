package no.difi.asic;

import no.difi.xsd.asic.model._1.AsicManifest;
import no.difi.xsd.asic.model._1.Certificate;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;

/**
 * Skeleton implementation of ASiC archive reader.
 *
 * @author Erlend Klakegg Bergheim
 */
class AbstractAsicReader {

    private static final Logger log = LoggerFactory.getLogger(AbstractAsicReader.class);

    private MessageDigest messageDigest;

    private AsicInputStream zipInputStream;
    private ZipEntry currentZipEntry;

    private ManifestVerifier manifestVerifier;

    AbstractAsicReader(MessageDigestAlgorithm messageDigestAlgorithm, InputStream inputStream) throws IOException {
        this.manifestVerifier = new ManifestVerifier(messageDigestAlgorithm);

        try {
            messageDigest = MessageDigest.getInstance(messageDigestAlgorithm.getAlgorithm());
            messageDigest.reset();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(String.format("Algorithm %s not supported", messageDigestAlgorithm.getAlgorithm()));
        }

        zipInputStream = new AsicInputStream(inputStream);
        // Comment in ZIP is stored in Central Directory in the end of the file.
    }

    /**
     * Provides the name of the next entry in the ASiC archive and positions the inputstream at the beginning of the data.
     *
     * @return name of next entry in archive.
     * @throws IOException
     */
    String getNextFile() throws IOException {
        while ((currentZipEntry = zipInputStream.getNextEntry()) != null) {
            log.info(String.format("Found file: %s", currentZipEntry.getName()));

            // Files used for validation is not exposed
            if (!specialFile())
                return currentZipEntry.getName();
        }

        manifestVerifier.verifyAllVerified();

        return null;
    }

    /**
     * Writes contents of current archive entry to the supplied output stream.
     * @param outputStream into which data from current entry should be written.
     * @throws IOException
     */
    void writeFile(OutputStream outputStream) throws IOException {
        if (currentZipEntry == null)
            throw new IllegalStateException("No file to read.");

        // Calculate digest while reading file
        messageDigest.reset();
        DigestOutputStream digestOutputStream = new DigestOutputStream(outputStream, messageDigest);
        IOUtils.copy(zipInputStream, digestOutputStream);

        zipInputStream.closeEntry();

        // Get digest
        byte[] digest = messageDigest.digest();
        log.debug(String.format("Digest: %s", new String(Base64.encode(digest))));

        manifestVerifier.update(currentZipEntry.getName(), digest, null);
    }

    /**
     * Closes the underlying zip input stream.
     *
     * @throws IOException
     */
    void close() throws IOException {
        if (zipInputStream != null) {
            zipInputStream.close();
            zipInputStream = null;
        }
    }

    ByteArrayOutputStream manifestStream = null;

    boolean specialFile() throws IOException {
        if (!currentZipEntry.getName().startsWith("META-INF/"))
            return false;

        String filename = currentZipEntry.getName().substring(9).toLowerCase();

        // Handling manifest in ASiC CAdES.
        if (filename.startsWith("asicmanifest")) {
            // Read content in manifest (also used for verification of signature)
            manifestStream = new ByteArrayOutputStream();
            IOUtils.copy(zipInputStream, manifestStream);

            CadesAsicManifest.extractAndVerify(new ByteArrayInputStream(manifestStream.toByteArray()), manifestVerifier);
            return true;
        }

        if (filename.startsWith("signature")) {
            if (filename.endsWith(".p7s")) {
                if (manifestStream != null) {
                    ByteArrayOutputStream signatureStream = new ByteArrayOutputStream();
                    IOUtils.copy(zipInputStream, signatureStream);

                    Certificate certificate = SignatureHelper.validate(manifestStream.toByteArray(), signatureStream.toByteArray());
                    certificate.setCert(currentZipEntry.getName());
                    manifestVerifier.addCertificate(certificate);
                }
            } else if (filename.endsWith(".xml")) {
                log.info("Found for XAdES: " + filename);

                manifestStream = new ByteArrayOutputStream();
                IOUtils.copy(zipInputStream, manifestStream);

                XadesAsicManifest.extractAndVerify(new ByteArrayInputStream(manifestStream.toByteArray()), manifestVerifier);
            }

            return true;
        }

        return false;
    }

    /**
     * Property getter for the AsicManifest of the ASiC archive.
     *
     * @return value of property.
     */
    public AsicManifest getAsicManifest() {
        return manifestVerifier.getAsicManifest();
    }

}
