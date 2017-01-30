/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.testtls.impl;

import de.rub.nds.tlsattacker.testtls.config.TestServerConfig;
import de.rub.nds.tlsattacker.tls.config.ConfigHandler;
import de.rub.nds.tlsattacker.tls.constants.AlertDescription;
import de.rub.nds.tlsattacker.tls.constants.AlertLevel;
import de.rub.nds.tlsattacker.tls.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.tls.exceptions.WorkflowExecutionException;
import de.rub.nds.tlsattacker.tls.protocol.ArbitraryMessage;
import de.rub.nds.tlsattacker.tls.protocol.alert.AlertMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ClientHelloMessage;
import de.rub.nds.tlsattacker.tls.workflow.TlsConfig;
import de.rub.nds.tlsattacker.tls.workflow.TlsContext;
import de.rub.nds.tlsattacker.tls.workflow.WorkflowExecutor;
import de.rub.nds.tlsattacker.tls.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.tls.workflow.action.MessageActionFactory;
import de.rub.nds.tlsattacker.transport.ConnectionEnd;
import de.rub.nds.tlsattacker.transport.TransportHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 
 * @author Juraj Somorovsky - juraj.somorovsky@rub.de
 */
public abstract class HandshakeTest extends TestTLS {

    static Logger LOGGER = LogManager.getLogger(HandshakeTest.class);

    final TestServerConfig serverConfig;

    TlsContext lastTlsContext;

    public HandshakeTest(ConfigHandler configHandler, TestServerConfig serverConfig) {
        super(configHandler);
        this.serverConfig = serverConfig;
    }

    boolean executeHandshake(TlsConfig tlsConfig) {
        TransportHandler transportHandler = configHandler.initializeTransportHandler(tlsConfig);

        TlsContext tlsContext = configHandler.initializeTlsContext(tlsConfig);
        tlsContext.setSelectedCipherSuite(tlsConfig.getSupportedCiphersuites().get(0));
        WorkflowTrace workflowTrace = new WorkflowTrace();
        ClientHelloMessage ch = new ClientHelloMessage();
        workflowTrace.add(MessageActionFactory.createAction(ConnectionEnd.CLIENT, ConnectionEnd.CLIENT, ch));
        workflowTrace.add(MessageActionFactory.createAction(ConnectionEnd.CLIENT, ConnectionEnd.SERVER,
                new ArbitraryMessage()));
        // we have to send this alert to make clear the connection will be
        // closed
        // and the server does not wait for further messages (there are test
        // servers,
        // for example Botan, for which closing connection is not enough)
        AlertMessage alert = new AlertMessage();
        alert.setLevel(AlertLevel.FATAL.getValue());
        alert.setDescription(AlertDescription.HANDSHAKE_FAILURE.getValue());// TODO
                                                                            // why
                                                                            // not
                                                                            // send
                                                                            // close
                                                                            // notify?
        workflowTrace.add(MessageActionFactory.createAction(ConnectionEnd.CLIENT, ConnectionEnd.CLIENT, alert));

        ch.setSupportedCipherSuites(tlsConfig.getSupportedCiphersuites());
        ch.setSupportedCompressionMethods(tlsConfig.getSupportedCompressionMethods());
        // TODO
        // WorkflowConfigurationFactory.initializeClientHelloExtensions(serverConfig,
        // ch);
        tlsContext.setWorkflowTrace(workflowTrace);
        // TODO
        // WorkflowConfigurationFactory.initializeProtocolMessageOrder(tlsContext);
        WorkflowExecutor workflowExecutor = configHandler.initializeWorkflowExecutor(transportHandler, tlsContext);
        lastTlsContext = tlsContext;
        try {
            workflowExecutor.executeWorkflow();
        } catch (WorkflowExecutionException ex) {
            LOGGER.info(ex.getLocalizedMessage());
            LOGGER.debug(ex.getLocalizedMessage(), ex);
        }
        transportHandler.closeConnection();
        return workflowTrace.getActuallyRecievedHandshakeMessagesOfType(HandshakeMessageType.SERVER_HELLO) != null;
    }

}
