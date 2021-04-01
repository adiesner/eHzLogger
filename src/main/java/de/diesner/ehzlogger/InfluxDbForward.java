package de.diesner.ehzlogger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openmuc.jsml.structures.SML_Message;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
import java.util.concurrent.TimeUnit;

public class InfluxDbForward extends TimerTask implements SmlForwarder {

    private final String remoteUri;
    private final String measurement;
    private final String username;
    private final String password;
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

    public InfluxDbForward(String remoteUri, String username, String password, String measurement, SmartMeterRegisterList smartMeterRegisterList,
        String bufferDirectoryPath) {
        this.remoteUri = remoteUri;
        this.username = username;
        this.password = password;
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
        ClientBuilder clientBuilder = ClientBuilder.newBuilder()
            .connectTimeout(1000, TimeUnit.MILLISECONDS)
            .readTimeout(5000, TimeUnit.MILLISECONDS);

        if ((password != null) && (username != null) && (username.trim().length() > 0)) {
            clientBuilder.register(new Authenticator(username, password));
        }

        client = clientBuilder.build();
        timer = new Timer();
        timer.schedule(this, 1000, 1000);
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
        final WebTarget target = client.target(remoteUri);
        final Response response;
        try {
            response = target.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.entity(dataToPost.getPostData(), MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (response.getStatus() != 204) {
            System.out.println("Failed : HTTP error code : " + response.getStatus());
            if (response.hasEntity()) {
                System.out.println(response.getEntity());
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
        if (bufferDirectory == null || toSave == null || toSave.size() == 0) {
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
                    System.out.println("Unable to delete buffer file: " + file.getPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
