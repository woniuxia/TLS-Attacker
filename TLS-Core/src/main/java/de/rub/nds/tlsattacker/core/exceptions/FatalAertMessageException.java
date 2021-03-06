/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2020 Ruhr University Bochum, Paderborn University,
 * and Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.exceptions;

/**
 * Configuration exception
 */
public class FatalAertMessageException extends RuntimeException {

    public FatalAertMessageException() {
        super();
    }

    public FatalAertMessageException(String message) {
        super(message);
    }

    public FatalAertMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
