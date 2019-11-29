package de.diesner.ehzlogger;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.LoggingFilter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openmuc.jsml.structures.*;

import java.util.*;

public class InfluxDbForward extends TimerTask implements SmlForwarder {

    private final String remoteUri;
    private final String measurement;
    private final Client client;
    private final SmartMeterRegisterList smartMeterRegisterList;
    private final List<DataToPost> postDataList = new ArrayList<>();

    private final Timer timer;

    @Getter
    @AllArgsConstructor
    private static class DataToPost {
        private String postData;
        private int retriesLeft;

        public void decRetriesLeft() {
            retriesLeft--;
        }
    }

    public InfluxDbForward(String remoteUri, String measurement, SmartMeterRegisterList smartMeterRegisterList) {
        this.remoteUri = remoteUri;
        this.measurement = measurement;
        this.smartMeterRegisterList = smartMeterRegisterList;
        ClientConfig clientConfig = new DefaultClientConfig();
        client = Client.create(clientConfig);
        timer = new Timer();
        timer.schedule(this, 1000, 1000);
    }

    public void enableHttpDebug() {
        client.addFilter(new LoggingFilter(System.out));
    }

    @Override
    public void messageReceived(List<SML_Message> messageList) {
        Map<String, String> values = SmlDecoder.extractValues(messageList, smartMeterRegisterList);
        addPostItem(getDataToPost(values));
    }

    private DataToPost getDataToPost(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return new DataToPost("", 0);
        }
        String postData = toLineProtocol(measurement, values);
        int MAXRETRYCOUNT = 3;
        return new DataToPost(postData, MAXRETRYCOUNT);
    }

    private String toLineProtocol(String measurement, Map<String, String> values) {
        StringBuffer data = new StringBuffer(measurement);
        boolean isFirst = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!isFirst) {
                data.append(",");
            } else {
                data.append(" ");
                isFirst = false;
            }
            data.append(entry.getKey()).append("=").append(entry.getValue());
        }

        data.append(" ").append(System.currentTimeMillis());
        return data.toString();
    }

    private boolean postData(DataToPost dataToPost) {
        if (dataToPost == null || dataToPost.getRetriesLeft() == 0) {
            return true;
        }
        WebResource webResource = client.resource(remoteUri);
        ClientResponse response = webResource.post(ClientResponse.class, dataToPost.getPostData());

        if (response.getStatus() != 204) {
            System.out.println("Failed : HTTP error code : " + response.getStatus());
            if (response.hasEntity()) {
                System.out.println(response.getEntity(String.class));
            }
            return false;
        }
        return true;
    }

    private DataToPost getPostItem() {
        synchronized (postDataList) {
            if (!postDataList.isEmpty()) {
                return postDataList.remove(0);
            }
        }
        return null;
    }

    private void addPostItem(DataToPost dataToPost) {
        if ((dataToPost != null) && (dataToPost.getRetriesLeft() > 0)) {
            synchronized (postDataList) {
                postDataList.add(dataToPost);
            }
        }
    }

    @Override
    public void run() {
        List<DataToPost> failedRequests = new ArrayList<>();
        DataToPost dataToPost;
        do {
            dataToPost = getPostItem();
            if (dataToPost != null && dataToPost.getRetriesLeft() > 0) {
                boolean success = false;
                try {
                    success = postData(dataToPost);
                } catch (Exception e) {
                    System.out.println("Exception while posting: " + e.getMessage());
                    e.printStackTrace();
                }
                if ((!success) && (dataToPost.getRetriesLeft() > 0)) {
                    failedRequests.add(dataToPost);
                }
            }
        } while (dataToPost != null);

        for (DataToPost retry: failedRequests) {
            retry.decRetriesLeft();
            addPostItem(retry);
        }
    }
}
