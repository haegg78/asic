package no.difi.asic;

import com.sun.xml.bind.api.JAXBRIContext;
import org.etsi.uri._2918.v1_1.ASiCManifestType;
import org.etsi.uri._2918.v1_1.DataObjectReferenceType;
import org.etsi.uri._2918.v1_1.ObjectFactory;
import org.etsi.uri._2918.v1_1.SigReferenceType;
import org.w3._2000._09.xmldsig_.DigestMethodType;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class AsicManifest {

    private static ObjectFactory objectFactory = new ObjectFactory();
    private static JAXBContext jaxbContext; // Thread safe

    static {
        try {
            jaxbContext = JAXBRIContext.newInstance(ASiCManifestType.class);
        } catch (JAXBException e) {
            throw new IllegalStateException(String.format("Unable to create JAXBContext: %s ", e.getMessage()), e);
        }
    }

    private MessageDigestAlgorithm messageDigestAlgorithm;
    private MessageDigest messageDigest;

    private ASiCManifestType ASiCManifestType = new ASiCManifestType();

    public AsicManifest(MessageDigestAlgorithm messageDigestAlgorithm) {
        this.messageDigestAlgorithm = messageDigestAlgorithm;

        // Create message digester
        try {
            messageDigest = MessageDigest.getInstance(messageDigestAlgorithm.getAlgorithm());
            messageDigest.reset();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(String.format("Algorithm %s not supported", messageDigestAlgorithm.getAlgorithm()));
        }
    }

    /**
     * Fetch a reset message digest.
     * @return Message digest ready for use.
     */
    MessageDigest getMessageDigest() {
        messageDigest.reset();
        return messageDigest;
    }

    /**
     *
     * @param filename Filename in container
     * @param mimeType Content type of content
     */
    public void add(String filename, String mimeType) {
        DataObjectReferenceType dataObject = new DataObjectReferenceType();
        dataObject.setURI(filename);
        dataObject.setMimeType(mimeType);
        dataObject.setDigestValue(messageDigest.digest());

        DigestMethodType digestMethodType = new DigestMethodType();
        digestMethodType.setAlgorithm(messageDigestAlgorithm.getUri());
        dataObject.setDigestMethod(digestMethodType);

        ASiCManifestType.getDataObjectReference().add(dataObject);
    }

    public void setSignature(String filename, String mimeType) {
        SigReferenceType sigReferenceType = new SigReferenceType();
        sigReferenceType.setURI(filename);
        sigReferenceType.setMimeType(mimeType);
        ASiCManifestType.setSigReference(sigReferenceType);
    }

    public ASiCManifestType getASiCManifestType() {
        return ASiCManifestType;
    }

    public byte[] toBytes() {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            JAXBElement<ASiCManifestType> jaxbRootElement = objectFactory.createASiCManifest(ASiCManifestType);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            marshaller.marshal(jaxbRootElement, baos);
            return baos.toByteArray();
        } catch (JAXBException e) {
            throw new IllegalStateException("Unable to marshall the ASiCManifest into string output", e);
        }
    }

}