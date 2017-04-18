package de.diesner.ehzlogger;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class InfluxDbForwardTest {

    InfluxDbForward influxDbForward;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
    private String serverPath = "/write?db=databaseName";
    private final String tableName = "datatable";

    @Before
    public void before() {
        influxDbForward = new InfluxDbForward("http://localhost:" + wireMockRule.port() + serverPath, tableName, new SmartMeterRegisterList());
        influxDbForward.enableHttpDebug();
    }

    @Test
    public void expectCorrectPost() throws InterruptedException {
        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(204)));

        influxDbForward.messageReceived(TestData.sampleMessage());
        influxDbForward.run(); // force flushing of data

        verify(1, postRequestedFor(urlEqualTo(serverPath))
                .withRequestBody(containing("datatable Wirkenergie_Tarif_2_Bezug=0.0,Wirkenergie_Tarif_1_Lieferung=1909170.0,Aktuelle_Gesamtwirkleistung=-138.3,Wirkenergie_Total_Lieferung=1909170.0,Wirkenergie_Total_Bezug=1618520.9,Wirkenergie_Tarif_2_Lieferung=0.0,Wirkenergie_Tarif_1_Bezug=1618520.9"))
        );
    }

    @Test
    public void expectOneRetry() throws InterruptedException {
        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(404)));

        influxDbForward.messageReceived(TestData.sampleMessage());
        influxDbForward.run(); // force flushing of data

        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(204)));
        influxDbForward.run(); // force flushing of data

        influxDbForward.run(); // force flushing of data (should not post again)

        verify(2, postRequestedFor(urlEqualTo(serverPath))
                .withRequestBody(containing("datatable Wirkenergie_Tarif_2_Bezug=0.0,Wirkenergie_Tarif_1_Lieferung=1909170.0,Aktuelle_Gesamtwirkleistung=-138.3,Wirkenergie_Total_Lieferung=1909170.0,Wirkenergie_Total_Bezug=1618520.9,Wirkenergie_Tarif_2_Lieferung=0.0,Wirkenergie_Tarif_1_Bezug=1618520.9"))
        );
    }

    @Test
    public void expectMaximumRetries() throws InterruptedException {
        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(404)));


        influxDbForward.messageReceived(TestData.sampleMessage());
        for (int i=0;i<10;i++) {
            influxDbForward.run(); // force flushing of data
        }

        verify(3, postRequestedFor(urlEqualTo(serverPath))
                .withRequestBody(containing("datatable Wirkenergie_Tarif_2_Bezug=0.0,Wirkenergie_Tarif_1_Lieferung=1909170.0,Aktuelle_Gesamtwirkleistung=-138.3,Wirkenergie_Total_Lieferung=1909170.0,Wirkenergie_Total_Bezug=1618520.9,Wirkenergie_Tarif_2_Lieferung=0.0,Wirkenergie_Tarif_1_Bezug=1618520.9"))
        );
    }


}
