package de.diesner.ehzlogger;

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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;

public class HttpPostForwardTest {

    HttpPostForward httpPostForward;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
    private String serverPath = "/write";
    private File bufferFolder;

    @Before
    @SneakyThrows
    public void before() {
        Properties properties = new Properties();
        bufferFolder = temporaryFolder.newFolder("buffer-"+ UUID.randomUUID());
        properties.load(getClass().getResourceAsStream("/application.properties"));
        httpPostForward = new HttpPostForward("http://localhost:" + wireMockRule.port() + serverPath, new SmartMeterRegisterList(properties), bufferFolder.getPath());
    }

    @Test
    public void expectCorrectPost() throws InterruptedException {
        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(204)));

        httpPostForward.messageReceived(TestData.sampleMessage());
        httpPostForward.run(); // force flushing of data

        verify(1, postRequestedFor(urlEqualTo(serverPath))
                .withRequestBody(equalToJson("[\n"
                    + "    {\n"
                    + "        \"Wirkenergie_Tarif_2_Bezug\": \"0.0\",\n"
                    + "        \"Wirkenergie_Tarif_1_Lieferung\": \"19091700.0\",\n"
                    + "        \"Aktuelle_Gesamtwirkleistung\": \"-1383.0\",\n"
                    + "        \"Wirkenergie_Total_Lieferung\": \"19091700.0\",\n"
                    + "        \"Wirkenergie_Total_Bezug\": \"16185209.0\",\n"
                    + "        \"Wirkenergie_Tarif_2_Lieferung\": \"0.0\",\n"
                    + "        \"Wirkenergie_Tarif_1_Bezug\": \"16185209.0\"\n"
                    + "    }\n"
                    + "]\n", true, true))
        );
    }

    @Test
    public void expectOneRetry() throws InterruptedException {
        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(404)));

        httpPostForward.messageReceived(TestData.sampleMessage());
        httpPostForward.run(); // force flushing of data

        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(204)));
        httpPostForward.run(); // force flushing of data

        httpPostForward.run(); // force flushing of data (should not post again)

        verify(2, postRequestedFor(urlEqualTo(serverPath))
            .withRequestBody(equalToJson("[\n"
                + "    {\n"
                + "        \"Wirkenergie_Tarif_2_Bezug\": \"0.0\",\n"
                + "        \"Wirkenergie_Tarif_1_Lieferung\": \"19091700.0\",\n"
                + "        \"Aktuelle_Gesamtwirkleistung\": \"-1383.0\",\n"
                + "        \"Wirkenergie_Total_Lieferung\": \"19091700.0\",\n"
                + "        \"Wirkenergie_Total_Bezug\": \"16185209.0\",\n"
                + "        \"Wirkenergie_Tarif_2_Lieferung\": \"0.0\",\n"
                + "        \"Wirkenergie_Tarif_1_Bezug\": \"16185209.0\"\n"
                + "    }\n"
                + "]\n", true, true))
        );
    }

    @Test
    public void isBufferedToDisk() {
        assertThat("No buffer file exists", bufferFolder.listFiles().length, CoreMatchers.equalTo(0));
        stubFor(post(urlEqualTo(serverPath)).willReturn(aResponse().withStatus(404)));
        for (int i=0;i<100;i++) {
            httpPostForward.messageReceived(TestData.sampleMessage());
        }

        for (int i=0;i<10;i++) {
            httpPostForward.run(); // force flushing of data
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
        httpPostForward.messageReceived(TestData.sampleMessage());

        for (int i=0;i<10;i++) {
            httpPostForward.run(); // force flushing of data
        }

        assertThat("Buffer Files have been removed", bufferFolder.listFiles().length, CoreMatchers.equalTo(0));

        verify(2, postRequestedFor(urlEqualTo(serverPath))
            .withRequestBody(equalToJson("[\n"
                + "    {\n"
                + "        \"Wirkenergie_Tarif_2_Bezug\": \"0.0\",\n"
                + "        \"Wirkenergie_Tarif_1_Lieferung\": \"19091700.0\",\n"
                + "        \"Aktuelle_Gesamtwirkleistung\": \"-1383.0\",\n"
                + "        \"Wirkenergie_Total_Lieferung\": \"19091700.0\",\n"
                + "        \"Wirkenergie_Total_Bezug\": \"16185209.0\",\n"
                + "        \"Wirkenergie_Tarif_2_Lieferung\": \"0.0\",\n"
                + "        \"Wirkenergie_Tarif_1_Bezug\": \"16185209.0\"\n"
                + "    }\n"
                + "]\n", true, true))
        );
    }

}
