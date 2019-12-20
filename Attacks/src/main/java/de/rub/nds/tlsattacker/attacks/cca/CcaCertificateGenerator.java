/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.attacks.cca;

import de.rub.nds.asn1.Asn1Encodable;
import de.rub.nds.asn1.encoder.Asn1EncoderForX509;
import de.rub.nds.asn1.model.*;
import de.rub.nds.asn1.parser.Asn1Parser;
import de.rub.nds.asn1.parser.ParserException;
import de.rub.nds.asn1.translator.ParseNativeTypesContext;
import de.rub.nds.asn1tool.filesystem.TextFileReader;
import de.rub.nds.asn1tool.xmlparser.Asn1XmlContent;
import de.rub.nds.asn1tool.xmlparser.XmlParser;
import de.rub.nds.modifiablevariable.util.Modifiable;
import de.rub.nds.tlsattacker.core.config.delegate.CcaDelegate;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateMessage;
import de.rub.nds.tlsattacker.core.protocol.message.cert.CertificatePair;
import de.rub.nds.x509attacker.keyfilemanager.KeyFileManager;
import de.rub.nds.x509attacker.keyfilemanager.KeyFileManagerException;
import de.rub.nds.x509attacker.linker.Linker;
import de.rub.nds.x509attacker.xmlsignatureengine.XmlSignatureEngine;

import javax.security.auth.x500.X500Principal;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static de.rub.nds.x509attacker.X509Attacker.*;

public class CcaCertificateGenerator {

    /**
     *
     * @param ccaDelegate
     * @param type
     * @return TODO: I might need access to a trust store. Or seperate
     *         directories for keys and certificates. It's likely that TODO: any
     *         integration of multiple certificates, partially with keys, will
     *         be implemented with x509 attacker.
     */
    public static CertificateMessage generateCertificate(CcaDelegate ccaDelegate, CcaCertificateType type)
            throws CertificateException, IOException, ParserException, KeyFileManagerException {
        CertificateMessage certificateMessage = new CertificateMessage();
        if (type != null) {
            switch (type) {
                case CLIENT_INPUT:
                    List<CertificatePair> certificatePairsList = new LinkedList<>();
                    CertificatePair certificatePair = new CertificatePair(ccaDelegate.getClientCertificate());
                    certificatePairsList.add(certificatePair);
                    certificateMessage.setCertificatesList(certificatePairsList);
                    break;
                case EMPTY:
                    certificateMessage.setCertificatesListBytes(Modifiable.explicit(new byte[0]));
                    break;
                case CA_LEAF_RSA:
                    generateCertificateMessageFromXML("root-v1.pem", "CA-LEAF_RSA-Basic.xml",
                            ccaDelegate.getKeyDirectory(), ccaDelegate.getXmlDirectory(), ccaDelegate.getCertificateInputDirectory(),
                            ccaDelegate.getCertificateOutputDirectory());
                default:
                    break;
            }
        }
        return certificateMessage;
    }

    /**
     * This function assumes that all paths and the corresponding files exist. It will be the task of the Probe to
     * ensure that those paths are at least set. Exceptions should be handled by the Probe
     *
     * This function establishes a few conventions. First, for every rootCertificate specifies there has to be a file
     * with the same name in the keyDirectory which is the corresponding PRIVATE key to the certificate. Additionally
     * several keys need to be present in the keyDirectory. Furthermore, certificateChains are XML files that can be
     * processed by X509Attacker with the sole exception of two placeholders which will be replaced. Those placeholders
     * are '<asn1Sequence identifier="issuer" type="Name" placeholder="replace_me"/>' and 'replace_me_im_a_dummy_key'.
     * The former is replaced with the subject of the root certificate used and the latter with the path to the key
     * of the root certificate. Note that not the attribute is the placeholder but the whole string. Last but not least
     * certificates have to be created in a certain order in the XML file because the code uses the key corresponding
     * to the last certificate for the connection, aka it's supposed to be the leaf certificate. All previous certificates
     * should be should be in descending order, starting at the top with the highest level CA.
     *
     *
     * @param rootCertificate
     * @param certificateChain
     * @param keyDirectory
     * @param xmlDirectory
     * @param certificateInputDirectory
     * @param certificateOutputDirectory
     * @return
     * @throws CertificateException
     * @throws IOException
     * @throws ParserException
     */
    private static CertificateMessage generateCertificateMessageFromXML(String rootCertificate, String certificateChain,
                                                                        String keyDirectory, String xmlDirectory,
                                                                        String certificateInputDirectory,
                                                                        String certificateOutputDirectory)
            throws CertificateException, IOException, ParserException, KeyFileManagerException {

        String xmlSubject = extractXMLCertificateSubject(rootCertificate);

        TextFileReader textFileReader = new TextFileReader(certificateChain);
        String xmlString = textFileReader.read();

        xmlString = xmlString.replace("<asn1Sequence identifier=\"issuer\" type=\"Name\" placeholder=\"replace_me\"/>", xmlSubject);
        xmlString = xmlString.replace("replace_me_im_a_dummy_key", keyDirectory + rootCertificate);

        // Parse XML
        XmlParser xmlParser = new XmlParser(xmlString);
        Asn1XmlContent asn1XmlContent = xmlParser.getAsn1XmlContent();
        Map<String, Asn1Encodable> identifierMap = xmlParser.getIdentifierMap();

        // Create links
        Linker linker = new Linker(identifierMap);

        // Load key files
        KeyFileManager keyFileManager = KeyFileManager.getReference();
        keyFileManager.init(keyDirectory);

        // Create signatures
        XmlSignatureEngine xmlSignatureEngine = new XmlSignatureEngine(linker, identifierMap);
        xmlSignatureEngine.computeSignatures();

        // Encode XML for certificate
        List<Asn1Encodable> certificates = asn1XmlContent.getAsn1Encodables();
        byte[][] encodedCertificates = new byte[certificates.size()][];
        for (int i = 0; i < certificates.size(); i++) {
            encodedCertificates[i] = Asn1EncoderForX509.encodeForCertificate(linker, certificates.get(i));
        }

        // Write certificate files such that test cases can be reproduced without TLS-Scanner with exactly the same certificates
        writeCertificates(certificateOutputDirectory, certificates, encodedCertificates);

        /**
         * TODO: add certificates in order to CertificateMessage and set keyPair according to leaf certs key
         */

        return new CertificateMessage();
    }

    private static String extractXMLCertificateSubject(String rootCertificate) throws CertificateException, IOException, ParserException {
        // Register XmlClasses, Types, Contexts and Unpackers
        registerXmlClasses();
        registerTypes();
        registerContexts();
        registerContentUnpackers();

        // Load X.509 root certificate and get Subject principal
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        FileInputStream fileInputStream = new FileInputStream(rootCertificate);
        X509Certificate x509Certificate = (X509Certificate) certificateFactory.generateCertificate(fileInputStream);
        X500Principal x500PrincipalSubject = x509Certificate.getSubjectX500Principal();

        // Get ASN.1 Subject
        Asn1Parser asn1Parser = new Asn1Parser(x500PrincipalSubject.getEncoded(), false);
        List<Asn1Encodable> asn1SubjectEncodables = asn1Parser.parse(ParseNativeTypesContext.NAME);

        // Loop variables
        Integer i = 0;
        Asn1Sequence _asn1Sequence;
        Asn1Encodable _asn1Encodable;
        Asn1ObjectIdentifier _asn1ObjectIdentifier;
        Asn1PrimitiveIa5String _asn1PrimitiveIa5String;
        Asn1PrimitivePrintableString _asn1PrimitivePrintableString;
        Asn1PrimitiveUtf8String _asn1PrimitiveUtf8String;

        // Output String
        StringBuilder stringBuilder = new StringBuilder();

        // Loop through parsed subject and construct XML String
        Asn1Sequence asn1Sequence = (Asn1Sequence) asn1SubjectEncodables.get(0);
        stringBuilder.append("<asn1Sequence identifier=\"issuer\" type=\"Name\">");
        stringBuilder.append("<asn1Set identifier=\"relativeDistinguishedName0\" type=\"RelativeDistinguishedName\">");
        for(Asn1Encodable asn1Set : asn1Sequence.getChildren()) {
            _asn1Sequence = (Asn1Sequence)((Asn1Set) asn1Set).getChildren().get(0);
            stringBuilder.append("<asn1Sequence identifier=\"attributeTypeAndValue" + i +"\" type=\"AttributeTypeAndValue\">");
            stringBuilder.append("<asn1ObjectIdentifier identifier=\"type\" type=\"AttributeType\">");

            _asn1ObjectIdentifier = (Asn1ObjectIdentifier) _asn1Sequence.getChildren().get(0);
            stringBuilder.append("<value>" + new StringBuilder(_asn1ObjectIdentifier.getValue().substring(0, 3)).reverse().toString() + _asn1ObjectIdentifier.getValue().substring(3) + "</value>");
            stringBuilder.append("</asn1ObjectIdentifier>");

            _asn1Encodable = _asn1Sequence.getChildren().get(1);
            if (_asn1Encodable instanceof Asn1PrimitiveUtf8String) {
                stringBuilder.append("<asn1PrimitiveUtf8String identifier=\"value\" type=\"AttributeValue\">");
                stringBuilder.append("<value>" + ((Asn1PrimitiveUtf8String) _asn1Encodable).getValue() + "</value>");
                stringBuilder.append("</asn1PrimitiveUtf8String>");

            } else if (_asn1Encodable instanceof Asn1PrimitivePrintableString) {
                stringBuilder.append("<asn1PrimitivePrintableString identifier=\"value\" type=\"AttributeValue\">");
                stringBuilder.append("<value>" + ((Asn1PrimitivePrintableString) _asn1Encodable).getValue() + "</value>");
                stringBuilder.append("</asn1PrimitivePrintableString>");
            } else if (_asn1Encodable instanceof Asn1PrimitiveIa5String) {
                stringBuilder.append("<asn1PrimitiveIa5String identifier=\"value\" type=\"AttributeValue\">");
                stringBuilder.append("<value>" + ((Asn1PrimitiveIa5String) _asn1Encodable).getValue() + "</value>");
                stringBuilder.append("</asn1PrimitiveIa5String>");
            } else {
                throw new ParserException("Parsing failed, encountered unknown Asn1 type in Subject value");
            }
            stringBuilder.append("</asn1Sequence>");
            i = i + 1;
        }
        stringBuilder.append("</asn1Set>");
        stringBuilder.append("</asn1Sequence>");

        return stringBuilder.toString();
    }

}
