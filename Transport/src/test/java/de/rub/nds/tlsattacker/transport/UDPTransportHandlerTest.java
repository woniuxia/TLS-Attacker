/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.transport;

import de.rub.nds.tlsattacker.util.ArrayConverter;
import de.rub.nds.tlsattacker.util.RandomHelper;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Florian Pfützenreuter <Florian.Pfuetzenreuter@rub.de>
 */
public class UDPTransportHandlerTest {

    private final InetAddress localhost = InetAddress.getLoopbackAddress();

    public UDPTransportHandlerTest() {

    }

    @Test
    public void testSendData() throws Exception {
        DatagramSocket testSocket = new DatagramSocket();

        UDPTransportHandler udpTH = new UDPTransportHandler(localhost.getHostName(), testSocket.getLocalPort(),
                ConnectionEnd.CLIENT, 400);
        testSocket.setSoTimeout(1000);

        udpTH.initialize();

        byte[] txData = new byte[8192];
        RandomHelper.getRandom().nextBytes(txData);
        byte[] rxData = new byte[8192];
        DatagramPacket rxPacket = new DatagramPacket(rxData, rxData.length, localhost, testSocket.getLocalPort());

        udpTH.sendData(txData);
        testSocket.receive(rxPacket);

        assertEquals("Confirm size of the sent data", txData.length, rxPacket.getLength());
        assertArrayEquals("Confirm sent data equals received data", txData, rxPacket.getData());

        udpTH.closeConnection();
        testSocket.close();
    }

    @Test
    public void testFetchData() throws Exception {
        DatagramSocket testSocket = new DatagramSocket();

        UDPTransportHandler udpTH = new UDPTransportHandler(localhost.getHostName(), testSocket.getLocalPort(),
                ConnectionEnd.CLIENT, 400);

        udpTH.initialize();
        testSocket.connect(localhost, udpTH.getLocalPort());
        udpTH.setTlsTimeout(1);

        byte[] allSentData = new byte[0];
        byte[] allReceivedData = new byte[0];
        byte[] txData;
        byte[] rxData;
        DatagramPacket txPacket;
        int numTestPackets = 100;

        for (int i = 0; i < numTestPackets; i++) {
            txData = new byte[RandomHelper.getRandom().nextInt(16383) + 1];
            RandomHelper.getRandom().nextBytes(txData);
            txPacket = new DatagramPacket(txData, txData.length, localhost, udpTH.getLocalPort());
            testSocket.send(txPacket);
            allSentData = ArrayConverter.concatenate(allSentData, txData);
            rxData = udpTH.fetchData();
            allReceivedData = ArrayConverter.concatenate(allReceivedData, rxData);
        }

        assertEquals("Confirm size of the received data", allSentData.length, allReceivedData.length);
        assertArrayEquals("Confirm received data equals sent data", allSentData, allReceivedData);

        udpTH.closeConnection();
        testSocket.close();
    }
}
