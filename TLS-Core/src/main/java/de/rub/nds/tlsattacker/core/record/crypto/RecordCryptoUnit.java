/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2020 Ruhr University Bochum, Paderborn University,
 * and Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.record.crypto;

import de.rub.nds.tlsattacker.core.record.cipher.RecordCipher;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class RecordCryptoUnit {

    private Logger LOGGER = LogManager.getLogger();

    protected ArrayList<RecordCipher> recordCipherList;

    public RecordCryptoUnit(RecordCipher recordCipher) {
        this.recordCipherList = new ArrayList<>();
        recordCipherList.add(0, recordCipher);
    }

    public RecordCipher getRecordMostRecentCipher() {
        return recordCipherList.get(recordCipherList.size() - 1);
    }

    public RecordCipher getRecordCipher(int epoch) {
        if (recordCipherList.size() > epoch) {
            return recordCipherList.get(epoch);
        } else {
            LOGGER.warn("Got no RecordCipher for epoch: " + epoch + " using epoch 0 cipher");
            return recordCipherList.get(0);
        }
    }

    public void addNewRecordCipher(RecordCipher recordCipher) {
        this.recordCipherList.add(recordCipher);
    }

}
