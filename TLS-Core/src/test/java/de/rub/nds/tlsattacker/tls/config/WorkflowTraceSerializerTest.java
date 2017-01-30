/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.tls.config;

import de.rub.nds.tlsattacker.tls.util.WorkflowTraceSerializer;
import de.rub.nds.tlsattacker.dtls.record.DtlsRecord;
import de.rub.nds.tlsattacker.modifiablevariable.ModifiableVariableFactory;
import de.rub.nds.tlsattacker.modifiablevariable.VariableModification;
import de.rub.nds.tlsattacker.modifiablevariable.integer.IntegerModificationFactory;
import de.rub.nds.tlsattacker.modifiablevariable.integer.ModifiableInteger;
import de.rub.nds.tlsattacker.tls.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.transport.ConnectionEnd;
import de.rub.nds.tlsattacker.tls.protocol.ProtocolMessage;
import de.rub.nds.tlsattacker.tls.protocol.handshake.ClientHelloMessage;
import de.rub.nds.tlsattacker.tls.record.Record;
import de.rub.nds.tlsattacker.tls.workflow.TlsConfig;
import de.rub.nds.tlsattacker.tls.workflow.TlsContext;
import de.rub.nds.tlsattacker.tls.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.tls.workflow.factory.WorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.tls.workflow.action.SendAction;
import de.rub.nds.tlsattacker.util.RandomHelper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import javax.xml.bind.JAXBException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 */
public class WorkflowTraceSerializerTest {

    private static final Logger LOGGER = LogManager.getLogger(WorkflowTraceSerializerTest.class);

    /**
     * Test of write method, of class WorkflowTraceSerializer.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testWriteRead() throws Exception {
        WorkflowConfigurationFactory factory = new WorkflowConfigurationFactory(new TlsConfig());
        WorkflowTrace trace = factory.createFullWorkflow();
        // pick random protocol message and initialize a record with modifiable
        // variable
        List<ProtocolMessage> pms = trace.getAllConfiguredMessages();
        int random = RandomHelper.getRandom().nextInt(pms.size());
        List<Record> records = new LinkedList<>();
        Record r = new Record();
        ModifiableInteger mv = ModifiableVariableFactory.createIntegerModifiableVariable();
        VariableModification<Integer> iam = IntegerModificationFactory.createRandomModification();
        iam.setPostModification(IntegerModificationFactory.explicitValue(random));
        mv.setModification(iam);
        r.setLength(mv);
        records.add(r);
        pms.get(random).setRecords(records);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        WorkflowTraceSerializer.write(os, trace);

        String serializedWorkflow = new String(os.toByteArray());

        LOGGER.debug(serializedWorkflow);
        System.out.println(serializedWorkflow);

        ByteArrayInputStream bis = new ByteArrayInputStream(serializedWorkflow.getBytes());
        WorkflowTrace wt = WorkflowTraceSerializer.read(bis);

        os = new ByteArrayOutputStream();
        WorkflowTraceSerializer.write(os, wt);

        System.out.println(new String(os.toByteArray()));

        Assert.assertArrayEquals("The serialized workflows have to be equal", serializedWorkflow.getBytes(),
                os.toByteArray());
    }

    /**
     * Test of write method, of class WorkflowTraceSerializer.
     * 
     * @throws java.lang.Exception
     */
    @Test
    public void testWriteReadDtls() throws Exception {
        TlsConfig config = new TlsConfig();
        config.setProtocolVersion(ProtocolVersion.DTLS12);
        WorkflowConfigurationFactory factory = new WorkflowConfigurationFactory(config);
        WorkflowTrace trace = factory.createFullWorkflow();

        // pick random protocol message and initialize a record with modifiable
        // variable
        List<ProtocolMessage> pms = trace.getAllConfiguredMessages();
        int random = RandomHelper.getRandom().nextInt(pms.size());
        List<Record> records = new LinkedList<>();
        DtlsRecord r = new DtlsRecord();
        ModifiableInteger mv = ModifiableVariableFactory.createIntegerModifiableVariable();
        VariableModification<Integer> iam = IntegerModificationFactory.createRandomModification();
        iam.setPostModification(IntegerModificationFactory.explicitValue(random));
        mv.setModification(iam);
        r.setLength(mv);
        records.add(r);
        pms.get(random).setRecords(records);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        WorkflowTraceSerializer.write(os, trace);

        String serializedWorkflow = new String(os.toByteArray());

        LOGGER.debug(serializedWorkflow);

        ByteArrayInputStream bis = new ByteArrayInputStream(serializedWorkflow.getBytes());
        WorkflowTrace wt = WorkflowTraceSerializer.read(bis);

        os = new ByteArrayOutputStream();
        WorkflowTraceSerializer.write(os, wt);

        Assert.assertArrayEquals("The serialized workflows have to be equal", serializedWorkflow.getBytes(),
                os.toByteArray());
    }

    @Test
    public void TestWrite() {
        try {
            WorkflowTrace trace = new WorkflowTrace();
            trace.add(new SendAction(new ClientHelloMessage()));
            File f = new File("workflowtrace.unittest");
            WorkflowTraceSerializer.write(f, trace);
            Assert.assertTrue(f.exists());
            f.delete();
        } catch (JAXBException | IOException ex) {
            LOGGER.error(ex.getLocalizedMessage(), ex);
            Assert.fail();
        }
    }

}
