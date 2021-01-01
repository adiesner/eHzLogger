package de.diesner.ehzlogger;

import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import org.openmuc.jsml.structures.SML_File;
import org.openmuc.jsml.tl.SML_SerialReceiver;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class EhzLogger {

    private SML_SerialReceiver receiver;
    private String port;
    private SmartMeterRegisterList smartMeterRegisterList;
    private List<SmlForwarder> forwarderList;
    private Properties properties;

    public static void main(String[] args) {
        EhzLogger ehzLogger;
        boolean keepRunning = true;

        while (keepRunning) {
            ehzLogger = new EhzLogger();
            try {
                keepRunning = ehzLogger.initialize(args);
                if (keepRunning)
                    ehzLogger.receiveMessageLoop();
            } catch (PortInUseException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (UnsupportedCommOperationException e) {
                e.printStackTrace();
            }
            ehzLogger.shutdown();
        }
    }

    /**
     * returns true on success
     * @param args command line arguments
     * @return
     * @throws PortInUseException
     * @throws IOException
     * @throws UnsupportedCommOperationException
     */
    private boolean initialize(String[] args) throws PortInUseException, IOException, UnsupportedCommOperationException {
        properties = new Properties();

        InputStream is;
        if (args.length == 1) {
            System.out.println("Loading properties file: "+args[0]);
            is = new FileInputStream(args[0]);
        } else {
            is = getClass().getResourceAsStream("/application.properties");
        }
        try {
            properties.load(is);
        } finally {
            is.close();
        }

        receiver = new SML_SerialReceiver();
        port = properties.getProperty("serial.port", "/dev/ttyUSB0");
        System.setProperty("gnu.io.rxtx.SerialPorts", port);

        smartMeterRegisterList = new SmartMeterRegisterList(properties);
        try {
            receiver.setupComPort(port);
        } catch (Exception e) {
            e.printStackTrace();
            receiver = null;
            return false;
        }

        forwarderList = new ArrayList<>();
        if (Boolean.parseBoolean(properties.getProperty("output.cmdline.enabled"))) {
            forwarderList.add(new CmdLinePrint(smartMeterRegisterList));
        }
        if (Boolean.parseBoolean(properties.getProperty("output.influxdb.enabled"))) {
            forwarderList.add(new InfluxDbForward(properties.getProperty("output.influxdb.remoteUri"),
                properties.getProperty("output.influxdb.measurement"),
                smartMeterRegisterList,
                properties.getProperty("output.influxdb.bufferdir"))
            );
        }
        if (Boolean.parseBoolean(properties.getProperty("output.posturl.enabled"))) {
            forwarderList.add(new HttpPostForward(properties.getProperty("output.posturl.remoteUri"), smartMeterRegisterList, properties.getProperty("output.posturl.bufferdir")));
        }

        if (forwarderList.isEmpty()) {
            return false;
        }
        return true;
    }

    private void receiveMessageLoop() throws IOException {

        while (true) {
            SML_File smlFile = receiver.getSMLFile();
            for (SmlForwarder forwarder : forwarderList) {
                forwarder.messageReceived(smlFile.getMessages());
            }
        }
    }

    private void shutdown() {
        try {
            if (receiver != null) {
                receiver.close();
            }
        } catch (IOException e) {
            System.err.println("Error while trying to close serial port: " + e.getMessage());
        }
    }

}
