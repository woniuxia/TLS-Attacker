/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.tls.config.delegate;

import com.beust.jcommander.Parameter;
import de.rub.nds.tlsattacker.tls.workflow.TlsConfig;

/**
 *
 * @author Robert Merget - robert.merget@rub.de
 */
public class VerifyDelegate extends Delegate {

    @Parameter(names = "-verify_workflow_correctness", description = "If this parameter is set, the workflow correctness is evaluated after the worklow stops. This involves"
            + "checks on the protocol message sequences.")
    private boolean verifyWorkflowCorrectness;

    public VerifyDelegate() {
    }

    public boolean isVerifyWorkflowCorrectness() {
        return verifyWorkflowCorrectness;
    }

    public void setVerifyWorkflowCorrectness(boolean verifyWorkflowCorrectness) {
        this.verifyWorkflowCorrectness = verifyWorkflowCorrectness;
    }

    @Override
    public void applyDelegate(TlsConfig config) {
        config.setVerifyWorkflow(verifyWorkflowCorrectness);
    }

}
