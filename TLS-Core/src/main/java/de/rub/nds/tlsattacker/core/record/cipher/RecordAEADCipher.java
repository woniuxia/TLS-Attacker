/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2020 Ruhr University Bochum, Paderborn University,
 * and Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.record.cipher;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.Bits;
import de.rub.nds.tlsattacker.core.constants.BulkCipherAlgorithm;
import de.rub.nds.tlsattacker.core.constants.ProtocolMessageType;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.crypto.cipher.CipherWrapper;
import de.rub.nds.tlsattacker.core.exceptions.CryptoException;
import de.rub.nds.tlsattacker.core.protocol.parser.Parser;
import de.rub.nds.tlsattacker.core.record.BlobRecord;
import de.rub.nds.tlsattacker.core.record.Record;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.KeySet;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import de.rub.nds.tlsattacker.transport.ConnectionEndType;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecordAEADCipher extends RecordCipher {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * AEAD tag length in bytes for regular ciphers
     */
    private static final int AEAD_TAG_LENGTH = 16;

    /**
     * AEAD tag length in bytes for CCM_8 ciphers
     */
    private static final int AEAD_CCM_8_TAG_LENGTH = 8;

    /**
     * Stores the computed tag length
     */
    private final int aeadTagLength;

    /**
     * Stores the computed tag length
     */
    private final int aeadExplicitLength;

    public RecordAEADCipher(TlsContext context, KeySet keySet) {
        super(context, keySet);
        ConnectionEndType localConEndType = context.getConnection().getLocalConnectionEndType();
        encryptCipher = CipherWrapper.getEncryptionCipher(cipherSuite, localConEndType, getKeySet());
        decryptCipher = CipherWrapper.getDecryptionCipher(cipherSuite, localConEndType, getKeySet());

        if (cipherSuite.isCCM_8()) {
            aeadTagLength = AEAD_CCM_8_TAG_LENGTH;
        } else {
            aeadTagLength = AEAD_TAG_LENGTH;
        }
        if (version.isTLS13()) {
            aeadExplicitLength = 0;
        } else {
            aeadExplicitLength = AlgorithmResolver.getCipher(cipherSuite).getNonceBytesFromRecord();
        }
    }

    public int getAeadSizeIncrease() {
        if (version.isTLS13()) {
            return aeadTagLength;
        } else {
            return aeadExplicitLength + aeadTagLength;
        }
    }

    private byte[] prepareEncryptionGcmNonce(byte[] aeadSalt, byte[] explicitNonce, Record record) {
        byte[] gcmNonce = ArrayConverter.concatenate(aeadSalt, explicitNonce);
        if (version.isTLS13() || bulkCipherAlg == BulkCipherAlgorithm.CHACHA20_POLY1305) {
            // Nonce construction is different for chacha & tls1.3
            gcmNonce = preprocessIv(record.getSequenceNumber().getValue().longValue(), gcmNonce);
        }
        record.getComputations().setGcmNonce(gcmNonce);
        gcmNonce = record.getComputations().getGcmNonce().getValue();
        return gcmNonce;
    }

    private byte[] prepareEncryptionAeadSalt(Record record) {
        byte[] aeadSalt = getKeySet().getWriteIv(context.getConnection().getLocalConnectionEndType());
        record.getComputations().setAeadSalt(aeadSalt);
        aeadSalt = record.getComputations().getAeadSalt().getValue();
        return aeadSalt;
    }

    private byte[] prepareEncryptionExplicitNonce(Record record) {
        byte[] explicitNonce = createExplicitNonce();
        record.getComputations().setExplicitNonce(explicitNonce);
        explicitNonce = record.getComputations().getExplicitNonce().getValue();
        return explicitNonce;
    }

    private byte[] createExplicitNonce() {
        byte[] explicitNonce;
        if (aeadExplicitLength > 0) {
            explicitNonce = ArrayConverter.longToBytes(context.getWriteSequenceNumber(), aeadExplicitLength);
        } else {
            explicitNonce = new byte[aeadExplicitLength];
        }
        return explicitNonce;
    }

    @Override
    public void encrypt(Record record) throws CryptoException {
        LOGGER.debug("Encrypting Record");
        record.getComputations().setCipherKey(getKeySet().getWriteKey(context.getChooser().getConnectionEndType()));
        if (version.isTLS13()) {
            int additionalPadding = context.getConfig().getDefaultAdditionalPadding();
            if (additionalPadding > 65536) {
                LOGGER.warn("Additional padding is too big. setting it to max possible value");
                additionalPadding = 65536;
            } else if (additionalPadding < 0) {
                LOGGER.warn("Additional padding is negative, setting it to 0");
                additionalPadding = 0;
            }
            record.getComputations().setPadding(new byte[additionalPadding]);
            record.getComputations().setPlainRecordBytes(
                    ArrayConverter.concatenate(record.getCleanProtocolMessageBytes().getValue(), new byte[] { record
                            .getContentType().getValue() }, record.getComputations().getPadding().getValue()));
            // For TLS1.3 we need the length beforehand to compute the
            // authenticatedMetaData
            record.setLength(record.getComputations().getPlainRecordBytes().getValue().length + AEAD_TAG_LENGTH);
            record.setContentType(ProtocolMessageType.APPLICATION_DATA.getValue());
        } else {
            record.getComputations().setPlainRecordBytes(record.getCleanProtocolMessageBytes().getValue());
        }

        byte[] explicitNonce = prepareEncryptionExplicitNonce(record);
        byte[] aeadSalt = prepareEncryptionAeadSalt(record);
        byte[] gcmNonce = prepareEncryptionGcmNonce(aeadSalt, explicitNonce, record);

        LOGGER.debug("Encrypting AEAD with the following IV: {}", ArrayConverter.bytesToHexString(gcmNonce));
        byte[] additionalAuthenticatedData = collectAdditionalAuthenticatedData(record, context.getChooser()
                .getSelectedProtocolVersion());
        record.getComputations().setAuthenticatedMetaData(additionalAuthenticatedData);
        additionalAuthenticatedData = record.getComputations().getAuthenticatedMetaData().getValue();

        LOGGER.debug("Encrypting AEAD with the following AAD: {}",
                ArrayConverter.bytesToHexString(additionalAuthenticatedData));

        byte[] plainBytes = record.getComputations().getPlainRecordBytes().getValue();
        byte[] wholeCipherText = encryptCipher.encrypt(gcmNonce, aeadTagLength * Bits.IN_A_BYTE,
                additionalAuthenticatedData, plainBytes);
        if (aeadTagLength >= wholeCipherText.length) {
            throw new CryptoException("Could not encrypt data. Supposed Tag is longer than the ciphertext");
        }
        byte[] onlyCiphertext = Arrays.copyOfRange(wholeCipherText, 0, wholeCipherText.length - aeadTagLength);

        record.getComputations().setAuthenticatedNonMetaData(onlyCiphertext);

        byte[] authenticationTag = Arrays.copyOfRange(wholeCipherText, wholeCipherText.length - aeadTagLength,
                wholeCipherText.length);

        record.getComputations().setAuthenticationTag(authenticationTag);
        authenticationTag = record.getComputations().getAuthenticationTag().getValue();

        record.getComputations().setCiphertext(onlyCiphertext);
        onlyCiphertext = record.getComputations().getCiphertext().getValue();

        record.setProtocolMessageBytes(ArrayConverter.concatenate(explicitNonce, onlyCiphertext, authenticationTag));
        // TODO our own authentication tags are always valid
        record.getComputations().setAuthenticationTagValid(true);

    }

    @Override
    public void decrypt(Record record) throws CryptoException {
        LOGGER.debug("Decrypting Record");
        record.getComputations().setCipherKey(getKeySet().getReadKey(context.getChooser().getConnectionEndType()));

        byte[] protocolBytes = record.getProtocolMessageBytes().getValue();
        DecryptionParser parser = new DecryptionParser(0, protocolBytes);

        byte[] explicitNonce = parser.parseByteArrayField(aeadExplicitLength);
        record.getComputations().setExplicitNonce(explicitNonce);
        explicitNonce = record.getComputations().getExplicitNonce().getValue();

        byte[] salt = getKeySet().getReadIv(context.getConnection().getLocalConnectionEndType());
        record.getComputations().setAeadSalt(salt);
        salt = record.getComputations().getAeadSalt().getValue();

        byte[] cipherTextOnly = parser.parseByteArrayField(parser.getBytesLeft() - aeadTagLength);
        record.getComputations().setCiphertext(cipherTextOnly);
        record.getComputations().setAuthenticatedNonMetaData(record.getComputations().getCiphertext().getValue());

        byte[] additionalAuthenticatedData = collectAdditionalAuthenticatedData(record, context.getChooser()
                .getSelectedProtocolVersion());
        record.getComputations().setAuthenticatedMetaData(additionalAuthenticatedData);
        additionalAuthenticatedData = record.getComputations().getAuthenticatedMetaData().getValue();

        LOGGER.debug("Decrypting AEAD with the following AAD: {}",
                ArrayConverter.bytesToHexString(additionalAuthenticatedData));

        byte[] gcmNonce = ArrayConverter.concatenate(salt, explicitNonce);
        if (version.isTLS13() || bulkCipherAlg == BulkCipherAlgorithm.CHACHA20_POLY1305) {
            // Nonce construction is different for chacha & tls1.3
            gcmNonce = preprocessIv(record.getSequenceNumber().getValue().longValue(), gcmNonce);
        }
        record.getComputations().setGcmNonce(gcmNonce);
        gcmNonce = record.getComputations().getGcmNonce().getValue();

        LOGGER.debug("Decrypting AEAD with the following IV: {}", ArrayConverter.bytesToHexString(gcmNonce));

        byte[] authenticationTag = parser.parseByteArrayField(parser.getBytesLeft());

        record.getComputations().setAuthenticationTag(authenticationTag);
        authenticationTag = record.getComputations().getAuthenticationTag().getValue();
        // TODO it would be better if we had a seperate CM implementation to do
        // the decryption

        try {
            byte[] plainRecordBytes = decryptCipher.decrypt(gcmNonce, aeadTagLength * Bits.IN_A_BYTE,
                    additionalAuthenticatedData, ArrayConverter.concatenate(cipherTextOnly, authenticationTag));

            record.getComputations().setAuthenticationTagValid(true);
            record.getComputations().setPlainRecordBytes(plainRecordBytes);
            plainRecordBytes = record.getComputations().getPlainRecordBytes().getValue();

            if (version.isTLS13()) {
                // TLS 1.3 plain record bytes are constructed as: Clean |
                // ContentType | 0x00000... (Padding)
                int numberOfPaddingBytes = countTrailingZeroBytes(plainRecordBytes);
                if (numberOfPaddingBytes == plainRecordBytes.length) {
                    LOGGER.warn("Record contains ONLY padding and no content type. Setting clean bytes == plainbytes");
                    record.setCleanProtocolMessageBytes(plainRecordBytes);
                    return;
                }
                parser = new DecryptionParser(0, plainRecordBytes);
                byte[] cleanBytes = parser.parseByteArrayField(plainRecordBytes.length - numberOfPaddingBytes - 1);
                byte[] contentType = parser.parseByteArrayField(1);
                byte[] padding = parser.parseByteArrayField(numberOfPaddingBytes);
                record.getComputations().setPadding(padding);
                record.setCleanProtocolMessageBytes(cleanBytes);
                record.getComputations().setPadding(cleanBytes);
                record.setContentType(contentType[0]);
                record.setContentMessageType(ProtocolMessageType.getContentType(contentType[0]));
            } else {
                record.setCleanProtocolMessageBytes(plainRecordBytes);
            }
        } catch (CryptoException E) {
            LOGGER.warn("Tag invalid", E);
            record.getComputations().setAuthenticationTagValid(false);
            throw new CryptoException(E);
        }
    }

    @Override
    public void encrypt(BlobRecord br) throws CryptoException {
        LOGGER.debug("Encrypting BlobRecord");
        br.setProtocolMessageBytes(encryptCipher.encrypt(br.getCleanProtocolMessageBytes().getValue()));
    }

    @Override
    public void decrypt(BlobRecord br) throws CryptoException {
        LOGGER.debug("Derypting BlobRecord");
        br.setCleanProtocolMessageBytes(decryptCipher.decrypt(br.getProtocolMessageBytes().getValue()));

    }

    private int countTrailingZeroBytes(byte[] plainRecordBytes) {
        int counter = 0;
        for (int i = plainRecordBytes.length - 1; i < plainRecordBytes.length; i--) {
            if (plainRecordBytes[i] == 0) {
                counter++;
            } else {
                return counter;
            }
        }
        return counter;
    }

    class DecryptionParser extends Parser<Object> {

        public DecryptionParser(int startposition, byte[] array) {
            super(startposition, array);
        }

        @Override
        public Object parse() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public byte[] parseByteArrayField(int length) {
            return super.parseByteArrayField(length);
        }

        @Override
        public int getBytesLeft() {
            return super.getBytesLeft();
        }

        @Override
        public int getPointer() {
            return super.getPointer();
        }

    }

    public byte[] preprocessIv(long sequenceNumber, byte[] iv) {
        byte[] padding = new byte[] { 0x00, 0x00, 0x00, 0x00 };
        byte[] temp = ArrayConverter.concatenate(padding, ArrayConverter.longToUint64Bytes(sequenceNumber));
        for (int i = 0; i < iv.length; ++i) {
            temp[i] ^= iv[i];
        }
        return temp;
    }
}
