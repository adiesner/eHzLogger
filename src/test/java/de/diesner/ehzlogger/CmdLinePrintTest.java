package de.diesner.ehzlogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class CmdLinePrintTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private SmartMeterRegisterList smartMeterRegisterList;

    @Before
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        smartMeterRegisterList = new SmartMeterRegisterList();
    }

    @After
    public void cleanUpStreams() {
        System.setOut(null);
        System.setErr(null);
    }

    @Test
    public void messageReceived() throws Exception {
        CmdLinePrint cmdLinePrint = new CmdLinePrint(smartMeterRegisterList);
        cmdLinePrint.messageReceived(TestData.sampleMessage());

        assertThat("Print output", outContent.toString(), equalTo(
                "Server-ID: 09:01:48:41:47:00:3B:9E:F9:98\n" +
                        "1-0:1.8.0*255 = 2167,9 Wh (Wirkenergie_Total_Bezug)\n" +
                        "1-0:1.8.1*255 = 1167,9 Wh (Wirkenergie_Tarif_1_Bezug)\n" +
                        "1-0:1.8.2*255 = 1000,0 Wh (Wirkenergie_Tarif_2_Bezug)\n" +
                        "1-0:2.8.0*255 = 2000,0 Wh (Wirkenergie_Total_Lieferung)\n" +
                        "1-0:2.8.1*255 = 1000,0 Wh (Wirkenergie_Tarif_1_Lieferung)\n" +
                        "1-0:2.8.2*255 = 1000,0 Wh (Wirkenergie_Tarif_2_Lieferung)\n" +
                        "1-0:16.7.0*255 = 0,0 W (Aktuelle_Gesamtwirkleistung)\n" +
                        "1-0:36.7.0*255 = 0,0 W (?)\n" +
                        "1-0:56.7.0*255 = 0,0 W (?)\n" +
                        "1-0:76.7.0*255 = 0,0 W (?)\n" +
                        "Server-ID: 06:49:53:4B:01:04:CE:E5:52:0E\n" +
                        "1-0:1.8.0*255 = 1618520,9 Wh (Wirkenergie_Total_Bezug)\n" +
                        "1-0:1.8.1*255 = 1618520,9 Wh (Wirkenergie_Tarif_1_Bezug)\n" +
                        "1-0:1.8.2*255 = 0,0 Wh (Wirkenergie_Tarif_2_Bezug)\n" +
                        "1-0:2.8.0*255 = 1909170,0 Wh (Wirkenergie_Total_Lieferung)\n" +
                        "1-0:2.8.1*255 = 1909170,0 Wh (Wirkenergie_Tarif_1_Lieferung)\n" +
                        "1-0:2.8.2*255 = 0,0 Wh (Wirkenergie_Tarif_2_Lieferung)\n" +
                        "1-0:16.7.0*255 = -138,3 W (Aktuelle_Gesamtwirkleistung)\n" +
                        "Server-ID: 09:01:48:41:47:00:3B:9E:F9:98\n" +
                        "1-0:1.8.0*255 = 2167,9 Wh (Wirkenergie_Total_Bezug)\n" +
                        "1-0:1.8.1*255 = 1167,9 Wh (Wirkenergie_Tarif_1_Bezug)\n" +
                        "1-0:1.8.2*255 = 1000,0 Wh (Wirkenergie_Tarif_2_Bezug)\n" +
                        "1-0:2.8.0*255 = 2000,0 Wh (Wirkenergie_Total_Lieferung)\n" +
                        "1-0:2.8.1*255 = 1000,0 Wh (Wirkenergie_Tarif_1_Lieferung)\n" +
                        "1-0:2.8.2*255 = 1000,0 Wh (Wirkenergie_Tarif_2_Lieferung)\n" +
                        "1-0:16.7.0*255 = 0,0 W (Aktuelle_Gesamtwirkleistung)\n" +
                        "1-0:36.7.0*255 = 0,0 W (?)\n" +
                        "1-0:56.7.0*255 = 0,0 W (?)\n" +
                        "1-0:76.7.0*255 = 0,0 W (?)\n" +
                        "Server-ID: 06:49:53:4B:01:04:CE:E5:52:0E\n" +
                        "1-0:1.8.0*255 = 1618520,9 Wh (Wirkenergie_Total_Bezug)\n" +
                        "1-0:1.8.1*255 = 1618520,9 Wh (Wirkenergie_Tarif_1_Bezug)\n" +
                        "1-0:1.8.2*255 = 0,0 Wh (Wirkenergie_Tarif_2_Bezug)\n" +
                        "1-0:2.8.0*255 = 1909170,0 Wh (Wirkenergie_Total_Lieferung)\n" +
                        "1-0:2.8.1*255 = 1909170,0 Wh (Wirkenergie_Tarif_1_Lieferung)\n" +
                        "1-0:2.8.2*255 = 0,0 Wh (Wirkenergie_Tarif_2_Lieferung)\n" +
                        "1-0:16.7.0*255 = -138,3 W (Aktuelle_Gesamtwirkleistung)\n\n"));
    }

}
