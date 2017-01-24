/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package tlsattacker.fuzzer.server;

import tlsattacker.fuzzer.exceptions.ServerDoesNotStartException;
import tlsattacker.fuzzer.helper.LogFileIDManager;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tlsattacker.fuzzer.config.FuzzerGeneralConfig;

/**
 * This Class represents a single Instance of an Implementation. The
 * Implementation can be started and restarted.
 * 
 * @author Robert Merget - robert.merget@rub.de
 */
public class TLSServer {

    private static final Logger LOGGER = LogManager.getLogger(TLSServer.class);

    /**
     * Server process
     */
    private Process p = null;

    /**
     * If the Server is currently used for fuzzing
     */
    private boolean free = true;

    /**
     * The IP the Server can be reached on
     */
    private String ip;

    /**
     * The port the Server can be reached on
     */
    private int port;

    /**
     * A unique ID
     */
    private int id = -1;

    /**
     * The command used to start the server
     */
    private String restartServerCommand;

    /**
     * String to wait for to indicate a started server
     */
    private String accepted;

    /**
     * Command that can be used to kill the server
     */
    private String killServerCommand = "";

    /**
     * Mayor Version of the TLSServer eg. Openssl, Botan etc
     */
    private String mayorVersion = "";

    /**
     * Minor Version of the TLSServer eg. 1.03g etc.
     */
    private String minorVersion = "";

    /**
     * The ErrorStream reader
     */
    private StreamGobbler errorGobbler;

    /**
     * The OutPutStream reader
     */
    private StreamGobbler outputGobbler;

    /**
     * The ProcessMonitor to monitor for the process end
     */
    private ProcessMonitor procmon = null;

    /**
     * FuzzerConfig to use
     */
    private FuzzerGeneralConfig config = null;

    /**
     * Exit code of the process
     */
    private int exitCode;

    // TODO akward constructor
    public TLSServer() {
        config = null;
        ip = null;
        port = 0;
        restartServerCommand = null;
    }

    /**
     * Creates a new TLSServer. TLSServers should be used in the
     * TLSServerManager
     * 
     * @param config
     *            The Config used
     * @param ip
     *            The IP of the Implementation
     * @param port
     *            The Port of the Implementation
     * @param restartServerCommand
     *            The command which should be executed to start the Server
     * @param accepted
     *            The String which the Server prints to the console when the
     *            Server is fully started
     * @param killServerCommand
     */
    public TLSServer(FuzzerGeneralConfig config, String ip, int port, String restartServerCommand, String accepted,
            String killServerCommand, String mayorVersion, String minorVersion) {
        this.config = config;
        this.ip = ip;
        this.port = port;
        this.restartServerCommand = restartServerCommand;
        this.accepted = accepted;
        this.killServerCommand = killServerCommand;
        this.mayorVersion = mayorVersion;
        this.minorVersion = minorVersion;
    }

    public synchronized String getMayorVersion() {
        return mayorVersion;
    }

    public synchronized void setMayorVersion(String mayorVersion) {
        this.mayorVersion = mayorVersion;
    }

    public synchronized String getMinorVersion() {
        return minorVersion;
    }

    public synchronized void setMinorVersion(String minorVersion) {
        this.minorVersion = minorVersion;
    }

    public synchronized String getKillServerCommand() {
        return killServerCommand;
    }

    public synchronized void setKillServerCommand(String killServerCommand) {
        this.killServerCommand = killServerCommand;
    }

    public synchronized String getAccepted() {
        return accepted;
    }

    public synchronized String getRestartServerCommand() {
        return restartServerCommand;
    }

    /**
     * Returns the IP of the Server
     * 
     * @return IP of the Server
     */
    public synchronized String getIp() {
        return ip;
    }

    /**
     * Returns the Port of the Server
     * 
     * @return Port of the Server
     */
    public int getPort() {
        return port;
    }

    /**
     * 
     * @param ip
     */
    public synchronized void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * 
     * @param port
     */
    public synchronized void setPort(int port) {
        this.port = port;
    }

    public synchronized void setRestartServerCommand(String restartServerCommand) {
        this.restartServerCommand = restartServerCommand;
    }

    public synchronized void setAccepted(String accepted) {
        this.accepted = accepted;
    }

    /**
     * Marks this Server. A Marked Server is currently used by the Fuzzer. A
     * Server should not be marked twice.
     */
    public synchronized void occupie() {
        if (this.free == false) {
            throw new IllegalStateException("Trying to occupie an already occupied Server");
        }
        this.free = false;
    }

    /**
     * Returns True if the Server is currently free to use
     * 
     * @return True if the Server is currently free to use
     */
    public synchronized boolean isFree() {
        return free;
    }

    /**
     * Releases an occupied Server, so that it can be used further for other
     * Testvectors
     */
    public synchronized void release() {
        if (this.free == true) {
            throw new IllegalStateException("Trying to release an already released Server");
        }
        this.free = true;
    }

    /**
     * Starts the Server by executing the restart Server command
     * 
     * @param keyFile
     */
    public synchronized void start(String prefix, File certificateFile, File keyFile) {

        // You have to ooccupie a Server to start it
        if (!this.isFree()) {
            if (p != null) {
                stop();
            }
            restart(prefix, certificateFile, keyFile);
        } else {
            throw new IllegalStateException("Cant start a not marked Server. Occupie it first!");
        }
    }

    /**
     * Restarts the Server by executing the restart Server command
     * 
     * @param prefix
     * @param certificateFile
     * @param keyFile
     */
    public synchronized void restart(String prefix, File certificateFile, File keyFile) {
        if (!this.isFree()) {
            if (p != null) {
                stop();
            }
            try {
                if (config.isRandomPort()) {
                    Random r = new Random();
                    port = 1024 + r.nextInt(4096);

                }
                id = LogFileIDManager.getInstance().getID();
                String command = (prefix + restartServerCommand).replace("[id]", "" + id);
                command = command.replace("[output]", config.getTracesFolder().getAbsolutePath());
                command = command.replace("[port]", "" + port);
                command = command.replace("[cert]", "" + certificateFile.getAbsolutePath());
                command = command.replace("[key]", "" + keyFile.getAbsolutePath());
                LOGGER.debug("Starting Server: " + command);
                long time = System.currentTimeMillis();
                Runtime rt = Runtime.getRuntime();
                p = rt.exec(command);

                // any error message?
                errorGobbler = new StreamGobbler(p.getErrorStream(), "ERR", accepted);
                errorGobbler.setName("Error Gobbler - " + id);
                // any output?
                outputGobbler = new StreamGobbler(p.getInputStream(), "OUT", accepted);
                outputGobbler.setName("Output Gobbler - " + id);
                // kick them off
                errorGobbler.start();
                outputGobbler.start();
                procmon = ProcessMonitor.create(p);
                while (!outputGobbler.accepted()) {

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        LOGGER.error(ex.getLocalizedMessage(), ex);
                    }
                    if (System.currentTimeMillis() - time >= config.getBootTimeout()) {
                        throw new ServerDoesNotStartException("Timeout in StreamGobler, Server never finished starting");
                    }
                }
            } catch (IOException ex) {
                LOGGER.error(ex.getLocalizedMessage(), ex);
            }

        } else {
            throw new IllegalStateException("Cant restart a not marked Server. Occupie it first!");
        }

    }

    /**
     * Checks if the server has booted
     * 
     * @return True if the server has booted
     */
    public synchronized boolean serverHasBooted() {
        return outputGobbler != null && outputGobbler.accepted() && p != null;
    }

    /**
     * Returns True if the Process the Server started has exited
     * 
     * @return True if the Process the Server started has exited
     */
    public synchronized boolean exited() {
        if (procmon == null) {
            throw new IllegalStateException("Server not yet Started!");
        } else {
            return procmon.isComplete();
        }

    }

    /**
     * Returns the ID assigned to the currently started Server, the ID changes
     * after every restart
     * 
     * @return
     * @returnID assigned to the currently started Server
     */
    public synchronized int getID() {
        return id;
    }

    @Override
    public synchronized String toString() {
        return "TLSServer{free=" + free + ", ip=" + ip + ", port=" + port + ", id=" + id + ", restartServerCommand="
                + restartServerCommand + ", accepted=" + accepted + '}';
    }

    /**
     * Stops the Server process
     */
    public synchronized void stop() {
        try {
            LOGGER.debug("Stopping Server");
            if (p != null) {

                p.destroy();
                exitCode = p.waitFor();
                if (config.isUseKill()) {
                    Runtime rt = Runtime.getRuntime();
                    p = rt.exec(killServerCommand);
                    p.waitFor();
                }
            }
        } catch (IOException | InterruptedException E) {
            E.printStackTrace();
        }
    }

    public synchronized FuzzerGeneralConfig getConfig() {
        return config;
    }

    public synchronized void setConfig(FuzzerGeneralConfig config) {
        this.config = config;
    }

    public int getExitCode() {
        return exitCode;
    }
}