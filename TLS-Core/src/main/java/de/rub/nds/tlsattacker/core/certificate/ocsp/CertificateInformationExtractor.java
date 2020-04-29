/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.certificate.ocsp;

import de.rub.nds.asn1.Asn1Encodable;
import de.rub.nds.asn1.model.Asn1EncapsulatingOctetString;
import de.rub.nds.asn1.model.Asn1Explicit;
import de.rub.nds.asn1.model.Asn1Integer;
import de.rub.nds.asn1.model.Asn1ObjectIdentifier;
import de.rub.nds.asn1.model.Asn1PrimitiveIa5String;
import de.rub.nds.asn1.model.Asn1Sequence;
import de.rub.nds.asn1.parser.Asn1Parser;
import de.rub.nds.asn1.parser.ParserException;
import de.rub.nds.asn1.translator.ParseOcspTypesContext;
import de.rub.nds.tlsattacker.core.util.Asn1ToolInitializer;
import org.bouncycastle.asn1.x509.Certificate;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static de.rub.nds.tlsattacker.core.certificate.ExtensionObjectIdentifier.*;

public class CertificateInformationExtractor {

    private final Certificate certificate;
    private List<Asn1Encodable> x509ExtensionSequences;
    private Asn1Sequence authorityInfoAccessEntities;
    private Boolean mustStaple;
    private Boolean mustStaplev2;
    private String ocspServerUrl;
    private String certificateIssuerUrl;

    public CertificateInformationExtractor(Certificate certificate) {
        this.certificate = certificate;

        // Init ASN.1 Tool
        Asn1ToolInitializer.initAsn1Tool();
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public BigInteger getSerialNumber() {
        return certificate.getSerialNumber().getValue();
    }

    public byte[] getIssuerNameHash() throws IOException, NoSuchAlgorithmException {
        byte[] encodedDistinguishedName = certificate.getIssuer().getEncoded();
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return md.digest(encodedDistinguishedName);
    }

    public byte[] getIssuerKeyHash() throws IOException, NoSuchAlgorithmException {
        byte[] publicKey = certificate.getSubjectPublicKeyInfo().getPublicKeyData().getBytes();
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return md.digest(publicKey);
    }

    public Boolean getMustStaple() throws IOException, ParserException {
        if (mustStaple == null) {
            mustStaple = parseMustStaple()[0];
        }
        return mustStaple;
    }

    public Boolean getMustStaplev2() throws IOException, ParserException {
        if (mustStaplev2 == null) {
            mustStaplev2 = parseMustStaple()[1];
        }
        return mustStaplev2;
    }

    public String getOcspServerUrl() throws IOException, ParserException, NoSuchFieldException {
        if (ocspServerUrl == null) {
            ocspServerUrl = parseOcspServerUrl();
        }

        return ocspServerUrl;
    }

    public String getCertificateIssuerUrl() throws IOException, ParserException, NoSuchFieldException {
        if (certificateIssuerUrl == null) {
            certificateIssuerUrl = parseCertificateIssuerUrl();
        }

        return certificateIssuerUrl;
    }

    private void retrieveX509Extensions() throws IOException, ParserException {
        String ocspUrlResult = null;

        byte[] certAsn1 = certificate.getEncoded();

        // Parse ASN.1 structure of the certificate
        Asn1Parser asn1Parser = new Asn1Parser(certAsn1, false);
        List<Asn1Encodable> asn1Encodables = asn1Parser.parse(ParseOcspTypesContext.NAME);

        /*
         * Navigate through the mess to the OCSP URL. First, just unroll the two
         * outer ASN.1 sequences to get to most of the information stored in a
         * X.509 certificate.
         */
        Asn1Sequence innerObjects = (Asn1Sequence) ((Asn1Sequence) asn1Encodables.get(0)).getChildren().get(0);

        // Get sequence containing X.509 extensions
        Asn1Explicit x509Extensions = null;

        for (Asn1Encodable enc : innerObjects.getChildren()) {
            if (enc instanceof Asn1Explicit) {
                /*
                 * -93 == 0xA3 signed == Offset 3. It's the explicit tag for
                 * X.509 extension in the DER encoded form, therefore a good
                 * value to search for.
                 */
                if (((Asn1Explicit) enc).getOffset() == 3) {
                    x509Extensions = (Asn1Explicit) enc;
                    break;
                }
            }
        }
        x509ExtensionSequences = ((Asn1Sequence) x509Extensions.getChildren().get(0)).getChildren();
    }

    private void retrieveAuthorityInfoAccessEntities() throws NoSuchFieldException {
        // Now that we found the extensions, search for the
        // 'authorityInfoAccess' extension
        Asn1Sequence authorityInfoAccess = null;

        for (Asn1Encodable enc : x509ExtensionSequences) {
            if (enc instanceof Asn1Sequence) {
                Asn1ObjectIdentifier objectIdentifier = (Asn1ObjectIdentifier) (((Asn1Sequence) enc).getChildren()
                        .get(0));
                // This is the objectIdentifier value for
                // authorityInfoAccess
                if (objectIdentifier.getValue().equals(AUTHORITY_INFO_ACCESS.getOID())) {
                    authorityInfoAccess = (Asn1Sequence) enc;
                    break;
                }
            }
        }

        if (authorityInfoAccess == null) {
            throw new NoSuchFieldException("No 'Authority Info Access' entry found in certificate.");
        }
        /*
         * get(0) is the Object Identifier we checked, get(1) the Octet String
         * with the content the Octet String has a sequence as child, and one of
         * them has the desired OCSP information. Almost there!
         */
        Asn1EncapsulatingOctetString authorityInfoAccessContent = (Asn1EncapsulatingOctetString) authorityInfoAccess
                .getChildren().get(1);

        this.authorityInfoAccessEntities = (Asn1Sequence) authorityInfoAccessContent.getChildren().get(0);
    }

    private boolean[] parseMustStaple() throws IOException, ParserException {
        if (x509ExtensionSequences == null) {
            retrieveX509Extensions();
        }

        boolean foundMustStaple = false;
        boolean foundMustStaplev2 = false;

        Asn1Sequence tlsFeatureExtension = null;

        for (Asn1Encodable enc : x509ExtensionSequences) {
            if (enc instanceof Asn1Sequence) {
                Asn1ObjectIdentifier objectIdentifier = (Asn1ObjectIdentifier) (((Asn1Sequence) enc).getChildren()
                        .get(0));
                // This is the objectIdentifier value for RFC 7633, which
                // defines the TLS feature X.509 extension
                if (objectIdentifier.getValue().equals(TLS_FEATURE.getOID())) {
                    tlsFeatureExtension = (Asn1Sequence) enc;
                    break;
                }
            }
        }

        if (tlsFeatureExtension != null) {
            Asn1EncapsulatingOctetString tlsFeaturesContent = (Asn1EncapsulatingOctetString) tlsFeatureExtension
                    .getChildren().get(1);
            Asn1Sequence tlsFeaturesContentSequence = (Asn1Sequence) tlsFeaturesContent.getChildren().get(0);

            /*
             * 5 = TLS extension "status_request", 17 = TLS extension
             * "status_request_v2". Taken from:
             * https://github.com/openssl/openssl
             * /blob/master/crypto/x509/v3_tlsf.c
             */

            for (Asn1Encodable feature : tlsFeaturesContentSequence.getChildren()) {
                if (feature instanceof Asn1Integer) {
                    if (((Asn1Integer) feature).getValue().intValue() == 5) {
                        foundMustStaple = true;
                    } else if (((Asn1Integer) feature).getValue().intValue() == 17) {
                        foundMustStaplev2 = true;
                    }
                }
            }

        }

        return new boolean[] { foundMustStaple, foundMustStaplev2 };
    }

    private String getStringFromInformationAccessEntry(List<Asn1Encodable> authorityInformationAccessInformation) {
        String urlString = null;
        if (authorityInformationAccessInformation != null) {
            Asn1PrimitiveIa5String urlIa5String = null;
            if (authorityInformationAccessInformation.size() > 1
                    && authorityInformationAccessInformation.get(1) instanceof Asn1PrimitiveIa5String) {
                urlIa5String = (Asn1PrimitiveIa5String) authorityInformationAccessInformation.get(1);
            }
            urlString = urlIa5String.getValue();
        }

        return urlString;
    }

    private String parseOcspServerUrl() throws IOException, ParserException, NoSuchFieldException {
        if (x509ExtensionSequences == null) {
            retrieveX509Extensions();
        }
        if (authorityInfoAccessEntities == null) {
            retrieveAuthorityInfoAccessEntities();
        }

        String ocspUrlResult = null;

        List<Asn1Encodable> ocspInformation = null;

        // Now let's check if we have OCSP information embedded...
        for (Asn1Encodable enc : authorityInfoAccessEntities.getChildren()) {
            if (enc instanceof Asn1Sequence) {
                Asn1ObjectIdentifier objectIdentifier = (Asn1ObjectIdentifier) ((Asn1Sequence) enc).getChildren()
                        .get(0);
                // This is the objectIdentifier value for OCSP
                if (objectIdentifier.getValue().equals(OCSP.getOID())) {
                    ocspInformation = ((Asn1Sequence) enc).getChildren();
                    break;
                }
            }
        }

        if (ocspInformation == null) {
            throw new NoSuchFieldException("No OCSP entry found in certificate.");
        }

        // If we found the OCSP information, let's extract it and we're
        // done!
        return getStringFromInformationAccessEntry(ocspInformation);
    }

    private String parseCertificateIssuerUrl() throws IOException, ParserException, NoSuchFieldException {
        if (x509ExtensionSequences == null) {
            retrieveX509Extensions();
        }
        if (authorityInfoAccessEntities == null) {
            retrieveAuthorityInfoAccessEntities();
        }

        String issuerCertUrlResult = null;

        List<Asn1Encodable> certificateIssuerInformation = null;

        // Now let's check if we have OCSP information embedded...
        for (Asn1Encodable enc : authorityInfoAccessEntities.getChildren()) {
            if (enc instanceof Asn1Sequence) {
                Asn1ObjectIdentifier objectIdentifier = (Asn1ObjectIdentifier) ((Asn1Sequence) enc).getChildren()
                        .get(0);
                // This is the objectIdentifier value for OCSP
                if (objectIdentifier.getValue().equals(CERTIFICATE_AUTHORITY_ISSUER.getOID())) {
                    certificateIssuerInformation = ((Asn1Sequence) enc).getChildren();
                    break;
                }
            }
        }

        if (certificateIssuerInformation == null) {
            throw new NoSuchFieldException("No Certificate Issuer entry found in certificate.");
        }

        // If we found the OCSP information, let's extract it and we're
        // done!
        return getStringFromInformationAccessEntry(certificateIssuerInformation);
    }
}
