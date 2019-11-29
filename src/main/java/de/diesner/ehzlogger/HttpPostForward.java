package de.diesner.ehzlogger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.openmuc.jsml.structures.SML_Message;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class HttpPostForward extends TimerTask implements SmlForwarder {

    private final String remoteUri;
    private final SmartMeterRegisterList smartMeterRegisterList;
    private final Client client;
    private final Timer timer;
    private final static int maximumMessagesBeforeCleanup = 32000;
    private final List<Map<String, String>> postDataList = new ArrayList<>();

    public HttpPostForward(String remoteUri, SmartMeterRegisterList smartMeterRegisterList) {
        this.remoteUri = remoteUri;
        this.smartMeterRegisterList = smartMeterRegisterList;

        ClientConfig clientConfig = new DefaultClientConfig();
        client = Client.create(clientConfig);
        timer = new Timer();
        timer.schedule(this, 1000, 1000);
    }

    @Override
    public void messageReceived(List<SML_Message> messageList) {
        Map<String, String> values = SmlDecoder.extractValues(messageList, smartMeterRegisterList);
        String FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
        LocalDateTime date = LocalDateTime.now();
        values.put("time", date.format(DateTimeFormatter.ofPattern(FORMAT)));
        synchronized (postDataList) {
            postDataList.add(values);
        }
    }

    @Override
    public void run() {
        List<Map<String, String>> dataForNextPost = new ArrayList<>();
        synchronized (postDataList) {
            while (dataForNextPost.size() < 100 && !postDataList.isEmpty()) {
                dataForNextPost.add(postDataList.remove(0));
            }
        }

        try {
            if (!postData(dataForNextPost)) {
                synchronized (postDataList) {
                    postDataList.addAll(dataForNextPost);
                }
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        //TODO: clean up memory if postDataList fills too much due to not reachable backend
        if (postDataList.size() > maximumMessagesBeforeCleanup) {
            cleanupMessages();
        }
    }

    /**
     * removes every second entry from postDataList
     */
    private void cleanupMessages() {
        List<Map<String, String>> newCleanedList = new ArrayList<>();
        synchronized (postDataList) {
            while (!postDataList.isEmpty()) {
                // add first item
                final Map<String, String> item = postDataList.remove(0);
                newCleanedList.add(item);
                // drop second item
                if (!postDataList.isEmpty()) {
                    postDataList.remove(0);
                }
            }
            postDataList.addAll(newCleanedList);
        }
    }

    private boolean postData(List<Map<String, String>> dataToPost) throws JsonProcessingException {
        if (dataToPost == null || dataToPost.isEmpty()) {
            return true;
        }

        ObjectMapper mapper = new ObjectMapper();
        final String json = mapper.writeValueAsString(dataToPost);
        WebResource webResource = client.resource(remoteUri);
        ClientResponse response = webResource.post(ClientResponse.class, json);

        if (response.getStatus() > 204) {
            System.out.println("Failed : HTTP error code : " + response.getStatus());
            if (response.hasEntity()) {
                System.out.println(response.getEntity(String.class));
            }
            return false;
        }
        return true;
    }

}
