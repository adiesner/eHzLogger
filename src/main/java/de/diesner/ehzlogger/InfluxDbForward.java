package de.diesner.ehzlogger;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.LoggingFilter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openmuc.jsml.structures.SML_Message;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class InfluxDbForward extends TimerTask implements SmlForwarder {

    private final String remoteUri;
    private final String measurement;
    private final Client client;
    private final SmartMeterRegisterList smartMeterRegisterList;
    private final List<DataToPost> postDataList = new ArrayList<>();

    private final Timer timer;
    private boolean isOnline;
    private Path bufferDirectory = null;

    @Getter
    @AllArgsConstructor
    private static class DataToPost {
        private String postData;
        private int retriesLeft;

        public void decRetriesLeft() {
            retriesLeft--;
        }
    }

    public InfluxDbForward(String remoteUri, String measurement, SmartMeterRegisterList smartMeterRegisterList, String bufferDirectoryPath) {
        this.remoteUri = remoteUri;
        this.measurement = measurement;
        this.smartMeterRegisterList = smartMeterRegisterList;
        if (bufferDirectoryPath != null && bufferDirectoryPath.trim().length() > 0) {
            bufferDirectory = Paths.get(bufferDirectoryPath);
            try {
                Files.createDirectories(bufferDirectory);
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed to create buffer directory: " + bufferDirectoryPath);
                bufferDirectory = null;
            }
        }
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
            return false;
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
        if (dataToPost != null) {
            synchronized (postDataList) {
                postDataList.add(dataToPost);
            }
        }
    }

    @Override
    public void run() {
        boolean lastIsOnline = isOnline;
        List<DataToPost> failedRequests = new ArrayList<>();
        DataToPost dataToPost;
        do {
            dataToPost = getPostItem();
            if (dataToPost != null && dataToPost.getRetriesLeft() > 0) {
                boolean success = false;
                try {
                    success = postData(dataToPost);
                    if (success) {
                        isOnline = true;
                    }
                } catch (Exception e) {
                    System.out.println("Exception while posting: " + e.getMessage());
                    e.printStackTrace();
                }
                if ((!success) && (dataToPost.getRetriesLeft() > 0)) {
                    failedRequests.add(dataToPost);
                    isOnline = false;
                }
            }
        } while (dataToPost != null);

        // switched from offline to online
        if ((lastIsOnline == false) && (isOnline == true)) {
            loadFromBuffer();
        }

        List<DataToPost> retriesGone = new ArrayList<>();
        for (DataToPost retry : failedRequests) {
            retry.decRetriesLeft();
            if (retry.getRetriesLeft() > 0) {
                addPostItem(retry);
            } else {
                retriesGone.add(retry);
            }
        }
        saveToBuffer(retriesGone);
    }

    private void saveToBuffer(List<DataToPost> toSave) {
        if (toSave == null || toSave.size() == 0) {
            return;
        }
        final Path bufferFile = bufferDirectory.resolve(System.currentTimeMillis() + ".txt");
        try (BufferedWriter writer = Files.newBufferedWriter(bufferFile, Charset.forName("UTF-8"))) {
            for (DataToPost data : toSave) {
                writer.write(data.getPostData() + System.lineSeparator());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadFromBuffer() {
        if (bufferDirectory == null) {
            return;
        }
        final File[] files = bufferDirectory.toFile().listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                List<String> allLines = Files.readAllLines(file.toPath());
                for (String line : allLines) {
                    addPostItem(new DataToPost(line, 3));
                }
                if (!file.delete()) {
                    System.out.println("Unable to delete buffer file: "+file.getPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
