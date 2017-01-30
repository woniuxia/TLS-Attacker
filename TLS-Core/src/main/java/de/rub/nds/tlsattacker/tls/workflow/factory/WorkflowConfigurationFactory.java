/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.tls.workflow.factory;

import de.rub.nds.tlsattacker.dtls.protocol.handshake.ClientHelloDtlsMessage;
import de.rub.nds.tlsattacker.dtls.protocol.handshake.HelloVerifyRequestMessage;
import de.rub.nds.tlsattacker.tls.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.tls.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.tls.protocol.ProtocolMessage;
import de.rub.nds.tlsattacker.tls.constants.CipherSuite;
import de.rub.nds.tlsattacker.transport.ConnectionEnd;
import de.rub.nds.tlsattacker.tls.protocol.application.ApplicationMessage;
import de.rub.nds.tlsattacker.tls.protocol.ccs.ChangeCipherSpecMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.CertificateMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.CertificateRequestMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.CertificateVerifyMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ClientHelloMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.DHClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.DHEServerKeyExchangeMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ECDHClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ECDHEServerKeyExchangeMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.FinishedMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.RSAClientKeyExchangeMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ServerHelloDoneMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ServerHelloMessage;
import de.rub.nds.tlsattacker.tls.protocol.heartbeat.HeartbeatMessage;
import de.rub.nds.tlsattacker.tls.workflow.TlsConfig;
import de.rub.nds.tlsattacker.tls.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.tls.workflow.action.MessageActionFactory;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 * @author Philip Riese <philip.riese@rub.de>
 */
public class WorkflowConfigurationFactory {

    static final Logger LOGGER = LogManager.getLogger(WorkflowConfigurationFactory.class);

    protected final TlsConfig config;

    public WorkflowConfigurationFactory(TlsConfig config) {
        this.config = config;
    }

    public WorkflowTrace createClientHelloWorkflow() {
        WorkflowTrace workflowTrace = new WorkflowTrace();
        List<ProtocolMessage> messages = new LinkedList<>();

        if (config.getBehaveLikeProtocolVersion() == ProtocolVersion.DTLS10
                || config.getBehaveLikeProtocolVersion() == ProtocolVersion.DTLS12) {
            ClientHelloDtlsMessage clientHello = new ClientHelloDtlsMessage();
            messages.add(clientHello);
            clientHello.setIncludeInDigest(false);
            workflowTrace.add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.CLIENT,
                    messages));
            messages = new LinkedList<>();
            HelloVerifyRequestMessage helloVerifyRequestMessage = new HelloVerifyRequestMessage();
            helloVerifyRequestMessage.setIncludeInDigest(false);
            messages.add(helloVerifyRequestMessage);
            workflowTrace.add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.SERVER,
                    messages));
            clientHello = new ClientHelloDtlsMessage();
            messages.add(clientHello);
            workflowTrace.add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.CLIENT,
                    messages));
        } else {
            ClientHelloMessage clientHello = new ClientHelloMessage();
            workflowTrace.add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.CLIENT,
                    messages));
        }
        return workflowTrace;
    }

    public WorkflowTrace createHandshakeWorkflow() {
        WorkflowTrace workflowTrace = this.createClientHelloWorkflow();
        List<ProtocolMessage> messages = new LinkedList<>();
        messages.add(new ServerHelloMessage());
        messages.add(new CertificateMessage());
        if (config.getSupportedCiphersuites().get(0).isEphemeral() && !config.isSessionResumption()) {
            addServerKeyExchangeMessage(messages);
        }
        if (config.isClientAuthentication() && !config.isSessionResumption()) {
            CertificateRequestMessage certRequest = new CertificateRequestMessage();
            certRequest.setRequired(false);
            messages.add(certRequest);
        }

        messages.add(new ServerHelloDoneMessage());
        workflowTrace
                .add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.SERVER, messages));
        messages = new LinkedList<>();
        if (config.isClientAuthentication() && !config.isSessionResumption()) {
            messages.add(new CertificateMessage());
            addClientKeyExchangeMessage(messages);
            messages.add(new CertificateVerifyMessage());
        } else {
            addClientKeyExchangeMessage(messages);
        }
        messages.add(new ChangeCipherSpecMessage());
        messages.add(new FinishedMessage());
        workflowTrace
                .add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.CLIENT, messages));
        messages = new LinkedList<>();
        messages.add(new ChangeCipherSpecMessage());
        messages.add(new FinishedMessage());
        workflowTrace
                .add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.SERVER, messages));
        return workflowTrace;

    }

    public void addClientKeyExchangeMessage(List<ProtocolMessage> messages) {
        if (config.isSessionResumption()) {
            return;
        }
        CipherSuite cs = config.getSupportedCiphersuites().get(0);
        switch (AlgorithmResolver.getKeyExchangeAlgorithm(cs)) {
            case RSA:
                messages.add(new RSAClientKeyExchangeMessage());
                break;
            case EC_DIFFIE_HELLMAN:
                messages.add(new ECDHClientKeyExchangeMessage());
                break;
            case DHE_DSS:
            case DHE_RSA:
            case DH_ANON:
            case DH_DSS:
            case DH_RSA:
                messages.add(new DHClientKeyExchangeMessage());
                break;
            default:
                LOGGER.info("Unsupported key exchange algorithm: " + AlgorithmResolver.getKeyExchangeAlgorithm(cs)
                        + ", not adding ClientKeyExchange Message");
                break;
        }
    }

    public void addServerKeyExchangeMessage(List<ProtocolMessage> messages) {
        CipherSuite cs = config.getSupportedCiphersuites().get(0);
        switch (AlgorithmResolver.getKeyExchangeAlgorithm(cs)) {
            case RSA:
                messages.add(new ECDHEServerKeyExchangeMessage());
                break;
            case EC_DIFFIE_HELLMAN:
                messages.add(new ECDHEServerKeyExchangeMessage());
                break;
            case DHE_DSS:
            case DHE_RSA:
            case DH_ANON:
            case DH_DSS:
            case DH_RSA:
                messages.add(new DHEServerKeyExchangeMessage());
                break;
            default:
                LOGGER.info("Unsupported key exchange algorithm: " + AlgorithmResolver.getKeyExchangeAlgorithm(cs)
                        + ", not adding ServerKeyExchange Message");
                break;
        }
    }

    /**
     * Creates an extended TLS workflow including an application data and
     * heartbeat messages
     *
     * @return
     */
    public WorkflowTrace createFullWorkflow() {
        WorkflowTrace workflowTrace = this.createHandshakeWorkflow();
        List<ProtocolMessage> messages = new LinkedList<>();
        if (config.isServerSendsApplicationData()) {
            messages.add(new ApplicationMessage());
            workflowTrace.add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.SERVER,
                    messages));
            messages = new LinkedList<>();
        }
        messages.add(new ApplicationMessage());

        if (config.getHeartbeatMode() != null) {
            messages.add(new HeartbeatMessage());
            workflowTrace.add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.CLIENT,
                    messages));
            messages = new LinkedList<>();
            messages.add(new HeartbeatMessage());
            workflowTrace.add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.SERVER,
                    messages));
        } else {
            workflowTrace.add(MessageActionFactory.createAction(config.getMyConnectionEnd(), ConnectionEnd.CLIENT,
                    messages));
        }
        return workflowTrace;
    }

    // /**
    // todo
    // * Initializes ClientHello extensions
    // *
    // * @param config
    // * @param ch
    // */
    // public static void initializeClientHelloExtensions(TlsConfig config,
    // ClientHelloMessage ch) {
    // if (config.getNamedCurves() != null &&
    // !config.getNamedCurves().isEmpty()) {
    // EllipticCurvesExtensionMessage ecc = new
    // EllipticCurvesExtensionMessage();
    // ecc.setSupportedCurvesConfig(config.getNamedCurves());
    // ch.addExtension(ecc);
    // }
    //
    // if (config.getPointFormats() != null &&
    // !config.getPointFormats().isEmpty()) {
    // ECPointFormatExtensionMessage pfc = new ECPointFormatExtensionMessage();
    // pfc.setPointFormatsConfig(config.getPointFormats());
    // ch.addExtension(pfc);
    // }
    //
    // if (config.getHeartbeatMode() != null) {
    // HeartbeatExtensionMessage hem = new HeartbeatExtensionMessage();
    // hem.setHeartbeatModeConfig(config.getHeartbeatMode());
    // ch.addExtension(hem);
    // }
    //
    // if (config.getSniHostname() != null) {
    // ServerNameIndicationExtensionMessage sni = new
    // ServerNameIndicationExtensionMessage();
    // sni.setNameTypeConfig(NameType.HOST_NAME);
    // sni.setServerNameConfig(config.getSniHostname());
    // ch.addExtension(sni);
    // }
    //
    // if (config.getMaxFragmentLength() != null) {
    // MaxFragmentLengthExtensionMessage mle = new
    // MaxFragmentLengthExtensionMessage();
    // mle.setMaxFragmentLengthConfig(MaxFragmentLength.getMaxFragmentLength(config.getMaxFragmentLength()
    // .getValue()));
    // ch.addExtension(mle);
    // }
    //
    // if (config.getSupportedSignatureAndHashAlgorithms() != null) {
    // SignatureAndHashAlgorithmsExtensionMessage sae = new
    // SignatureAndHashAlgorithmsExtensionMessage();
    // sae.setSignatureAndHashAlgorithmsConfig(config.getSupportedSignatureAndHashAlgorithms());
    // ch.addExtension(sae);
    // }
    // }
    // /** todo
    // * Initializes the preconfigured protocol message order according to the
    // * workflow trace. This protocol message order can be used to compare the
    // * configured and real message order.
    // *
    // * @param context
    // */
    // public static void initializeProtocolMessageOrder(TlsContext context) {
    // List<ProtocolMessageTypeHolder> configuredProtocolMessageOrder = new
    // LinkedList<>();
    // for (ProtocolMessage pm :
    // context.getWorkflowTrace().getAllConfiguredMessages()) {
    // ProtocolMessageTypeHolder pmth = new ProtocolMessageTypeHolder(pm);
    // configuredProtocolMessageOrder.add(pmth);
    // }
    // }
}
