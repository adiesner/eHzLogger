package de.diesner.ehzlogger;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Properties;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class HttpPostForwardTest {

    HttpPostForward httpPostForward;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
    private String serverPath = "/write";

    @Before
    @SneakyThrows
    public void before() {
        Properties properties = new Properties();
        properties.load(getClass().getResourceAsStream("/application.properties"));
        httpPostForward = new HttpPostForward("http://localhost:" + wireMockRule.port() + serverPath, new SmartMeterRegisterList(properties));
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
                    + "        \"Wirkenergie_Tarif_1_Lieferung\": \"1909170.0\",\n"
                    + "        \"Aktuelle_Gesamtwirkleistung\": \"-138.3\",\n"
                    + "        \"Wirkenergie_Total_Lieferung\": \"1909170.0\",\n"
                    + "        \"Wirkenergie_Total_Bezug\": \"1618520.9\",\n"
                    + "        \"Wirkenergie_Tarif_2_Lieferung\": \"0.0\",\n"
                    + "        \"Wirkenergie_Tarif_1_Bezug\": \"1618520.9\"\n"
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
                + "        \"Wirkenergie_Tarif_1_Lieferung\": \"1909170.0\",\n"
                + "        \"Aktuelle_Gesamtwirkleistung\": \"-138.3\",\n"
                + "        \"Wirkenergie_Total_Lieferung\": \"1909170.0\",\n"
                + "        \"Wirkenergie_Total_Bezug\": \"1618520.9\",\n"
                + "        \"Wirkenergie_Tarif_2_Lieferung\": \"0.0\",\n"
                + "        \"Wirkenergie_Tarif_1_Bezug\": \"1618520.9\"\n"
                + "    }\n"
                + "]\n", true, true))
        );
    }

}
