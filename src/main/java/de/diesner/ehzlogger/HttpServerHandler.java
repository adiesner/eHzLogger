package de.diesner.ehzlogger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.openmuc.jsml.structures.SML_Message;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpServerHandler implements HttpHandler, SmlForwarder {
    private final SmartMeterRegisterList smartMeterRegisterList;
    private Map<String, Double> values = new HashMap<>();
    private ObjectMapper mapper = new ObjectMapper();

    public HttpServerHandler(SmartMeterRegisterList smartMeterRegisterList) {
        this.smartMeterRegisterList = smartMeterRegisterList;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        OutputStream outputStream = httpExchange.getResponseBody();
        String response = getBody();

        httpExchange.getResponseHeaders().put("Content-Type", Arrays.asList("application/json"));
        httpExchange.sendResponseHeaders(200, response.length());
        outputStream.write(response.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        outputStream.close();
    }

    private String getBody() {
        try {
            return mapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return e.toString();
        }
    }

    @Override
    public void messageReceived(List<SML_Message> messageList) {
        values = toDoubleValues(SmlDecoder.extractValues(messageList, smartMeterRegisterList));
    }

    private Map<String, Double> toDoubleValues(Map<String, String> list) {
        Map<String, Double> values = new HashMap<>();
        for (Map.Entry<String, String> entry : list.entrySet()) {
            try {
                values.put(entry.getKey(), Double.valueOf(entry.getValue()));
            } catch (NumberFormatException e) {
            }
        }
        return values;
    }
}
