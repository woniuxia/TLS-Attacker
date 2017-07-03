/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.serializer;

import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.protocol.message.ChangeCipherSpecMessage;

/**
 *
 * @author Robert Merget - robert.merget@rub.de
 */
public class ChangeCipherSpecSerializer extends ProtocolMessageSerializer<ChangeCipherSpecMessage> {

    private final ChangeCipherSpecMessage msg;

    /**
     * Constructor for the ChangerCipherSpecSerializer
     *
     * @param message
     *            Message that should be serialized
     * @param version
     *            Version of the Protocol
     */
    public ChangeCipherSpecSerializer(ChangeCipherSpecMessage message, ProtocolVersion version) {
        super(message, version);
        this.msg = message;
    }

    @Override
    public byte[] serializeProtocolMessageContent() {
        LOGGER.debug("Serializing ChangeCipherSepcMessage");
        writeCcsProtocolType(msg);
        return getAlreadySerialized();
    }

    /**
     * Writes the CcsPrtotocolType of the ChangeCipherSpecMessage into the final
     * byte[]
     */
    private void writeCcsProtocolType(ChangeCipherSpecMessage msg) {
        appendByte(msg.getCcsProtocolType().getValue());
        LOGGER.debug("CcsProtocolType: " + msg.getCcsProtocolType().getValue());
    }

}
