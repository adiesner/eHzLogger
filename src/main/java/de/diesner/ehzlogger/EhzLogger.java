package de.diesner.ehzlogger;

import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import org.openmuc.jsml.structures.SML_File;
import org.openmuc.jsml.tl.SML_SerialReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EhzLogger {

    public static void main(String[] args) throws IOException, PortInUseException, UnsupportedCommOperationException {
        System.setProperty("gnu.io.rxtx.SerialPorts", "/dev/ttyUSB0");
        final SML_SerialReceiver receiver = new SML_SerialReceiver();
        receiver.setupComPort("/dev/ttyUSB0");

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run()
            {
                try {
                    receiver.close();

                } catch (IOException e) {
                    System.err.println("Error while trying to close serial port: " + e.getMessage());
                }
            }
        });

        List<SmlForwarder> forwarderList = new ArrayList<>();
        forwarderList.add(new CmdLinePrint());
        forwarderList.add(new InfluxDbForward("http://localhost:8086/write?db=home&precision=ms", "strom"));

        while (true) {
            SML_File smlFile = receiver.getSMLFile();
            for (SmlForwarder forwarder : forwarderList) {
                forwarder.messageReceived(smlFile.getMessages());
            }
        }
    }

}
