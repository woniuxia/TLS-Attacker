/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.tls.protocol.handshake;

import de.rub.nds.tlsattacker.tls.protocol.handshake.handler.CertificateHandler;
import de.rub.nds.tlsattacker.modifiablevariable.ModifiableVariableFactory;
import de.rub.nds.tlsattacker.modifiablevariable.ModifiableVariableProperty;
import de.rub.nds.tlsattacker.modifiablevariable.bytearray.ModifiableByteArray;
import de.rub.nds.tlsattacker.modifiablevariable.integer.ModifiableInteger;
import de.rub.nds.tlsattacker.transport.ConnectionEnd;
import de.rub.nds.tlsattacker.tls.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.tls.protocol.ProtocolMessageHandler;
import de.rub.nds.tlsattacker.tls.workflow.TlsContext;
import de.rub.nds.tlsattacker.util.ArrayConverter;
import javax.xml.bind.annotation.XmlTransient;
import org.bouncycastle.jce.provider.X509CertificateObject;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 */
public class CertificateMessage extends HandshakeMessage {

    /**
     * certificates length
     */
    @ModifiableVariableProperty(type = ModifiableVariableProperty.Type.LENGTH)
    private ModifiableInteger certificatesLength;

    // List<ModifiableInteger> certificateLengths;
    //
    // List<Certificate> certificates;
    @ModifiableVariableProperty(format = ModifiableVariableProperty.Format.ASN1, type = ModifiableVariableProperty.Type.CERTIFICATE)
    private ModifiableByteArray x509CertificateBytes;

    public CertificateMessage() {
        super(HandshakeMessageType.CERTIFICATE);
    }

    public ModifiableInteger getCertificatesLength() {
        return certificatesLength;
    }

    public void setCertificatesLength(ModifiableInteger certificatesLength) {
        this.certificatesLength = certificatesLength;
    }

    public void setCertificatesLength(int length) {
        this.certificatesLength = ModifiableVariableFactory.safelySetValue(certificatesLength, length);
    }

    public ModifiableByteArray getX509CertificateBytes() {
        return x509CertificateBytes;
    }

    public void setX509CertificateBytes(ModifiableByteArray x509CertificateBytes) {
        this.x509CertificateBytes = x509CertificateBytes;
    }

    public void setX509CertificateBytes(byte[] array) {
        this.x509CertificateBytes = ModifiableVariableFactory.safelySetValue(x509CertificateBytes, array);
    }

    // public List<ModifiableInteger> getCertificateLengths() {
    // return certificateLengths;
    // }
    //
    // public void setCertificateLengths(List<ModifiableInteger>
    // certificateLengths) {
    // this.certificateLengths = certificateLengths;
    // }
    //
    // public void addCertificateLength(int length) {
    // if (this.certificateLengths == null) {
    // this.certificateLengths = new LinkedList<>();
    // }
    // ModifiableInteger mv = new ModifiableVariable<>();
    // mv.setOriginalValue(length);
    // this.certificateLengths.add(mv);
    // }
    //
    // public List<Certificate> getCertificates() {
    // return certificates;
    // }
    //
    // public void setCertificates(List<Certificate> certificates) {
    // this.certificates = certificates;
    // }
    //
    // public void addCertificate(Certificate cert) {
    // if (this.certificates == null) {
    // this.certificates = new LinkedList<>();
    // }
    // this.certificates.add(cert);
    // }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (certificatesLength != null) {
            sb.append("\n  Certificates Length: ");
            sb.append(certificatesLength.getValue());
        }
        if (x509CertificateBytes != null) {
            sb.append("\n  Certificate:\n");
            sb.append(ArrayConverter.bytesToHexString(x509CertificateBytes.getValue()));
        }
        return sb.toString();
    }

    // public PublicKey getPublicKey() {
    // Certificate cert = certificates.get(0);
    // return cert.getPublicKey();
    // }
    @Override
    public ProtocolMessageHandler getProtocolMessageHandler(TlsContext tlsContext) {
        ProtocolMessageHandler handler = new CertificateHandler(tlsContext);
        handler.setProtocolMessage(this);
        return handler;
    }
}
