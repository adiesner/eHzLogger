package de.diesner.ehzlogger;

import java.io.IOException;
import java.util.List;

import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import org.openmuc.jsml.structures.*;
import org.openmuc.jsml.tl.SML_SerialReceiver;

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

        CmdLinePrint cmdLinePrint = new CmdLinePrint();

        while (true) {

            SML_File smlFile = receiver.getSMLFile();
            System.out.println("Got SML_File");

            cmdLinePrint.messageReceived(smlFile.getMessages());
        }
    }

}
