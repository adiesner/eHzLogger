package de.diesner.ehzlogger;

import com.github.tomakehurst.wiremock.client.BasicCredentials;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import lombok.SneakyThrows;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Properties;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;

public class InfluxDbForwardTest {

    InfluxDbForward influxDbForward;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
    private String serverPath = "/write?db=databaseName";
    private final String tableName = "datatable";
    private File bufferFolder;
    private Properties properties;

    @Before
    @SneakyThrows
    public void before() {
        properties = new Properties();
        properties.load(getClass().getResourceAsStream("/application.properties"));
        bufferFolder= temporaryFolder.newFolder("buffer-"+ UUID.randomUUID());
        influxDbForward = new InfluxDbForward("http://localhost:" + wireMockRule.port() + serverPath, "", "", tableName, new SmartMeterRegisterList(properties), bufferFolder.getPath());
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
    public void expectBasicAuth() {
        String username = "sampleUser";
        String password = "samplePassword";
        influxDbForward = new InfluxDbForward("http://localhost:" + wireMockRule.port() + serverPath, username, password, tableName, new SmartMeterRegisterList(properties), bufferFolder.getPath());

        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(204)));

        influxDbForward.messageReceived(TestData.sampleMessage());
        influxDbForward.run(); // force flushing of data

        verify(1, postRequestedFor(urlEqualTo(serverPath))
            .withBasicAuth(new BasicCredentials(username, password))
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

    @Test
    public void isBufferedToDisk() {
        assertThat("No buffer file exists", bufferFolder.listFiles().length, CoreMatchers.equalTo(0));
        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(404)));
        for (int i=0;i<100;i++) {
            influxDbForward.messageReceived(TestData.sampleMessage());
        }

        for (int i=0;i<10;i++) {
            influxDbForward.run(); // force flushing of data
        }

        File[] files = bufferFolder.listFiles();
        assertThat("Buffer files have been created", files.length, CoreMatchers.not(0));
    }

    @Test
    public void resumeFromBufferIfOnlineAgain() {
        isBufferedToDisk();
        assertThat("Buffer files exist", bufferFolder.listFiles().length, CoreMatchers.not(0));

        // reset Statistics
        WireMock.reset();
        // endpoint is back online...
        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(204)));
        // receive message
        influxDbForward.messageReceived(TestData.sampleMessage());

        for (int i=0;i<10;i++) {
            influxDbForward.run(); // force flushing of data
        }

        assertThat("Buffer Files have been removed", bufferFolder.listFiles().length, CoreMatchers.equalTo(0));
        verify(101, postRequestedFor(urlEqualTo(serverPath))
            .withRequestBody(containing("datatable Wirkenergie_Tarif_2_Bezug=0.0,Wirkenergie_Tarif_1_Lieferung=1909170.0,Aktuelle_Gesamtwirkleistung=-138.3,Wirkenergie_Total_Lieferung=1909170.0,Wirkenergie_Total_Bezug=1618520.9,Wirkenergie_Tarif_2_Lieferung=0.0,Wirkenergie_Tarif_1_Bezug=1618520.9"))
        );
    }


}
