package de.diesner.ehzlogger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.openmuc.jsml.structures.SML_Message;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final static int maximumMessagesBeforeBuffer = 10;
    private final List<Map<String, String>> postDataList = new ArrayList<>();
    private Path bufferDirectory = null;
    private ObjectMapper mapper = new ObjectMapper();
    private boolean isOnline;

    public HttpPostForward(String remoteUri, SmartMeterRegisterList smartMeterRegisterList, String bufferDirectoryPath) {
        this.remoteUri = remoteUri;
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
        boolean lastIsOnline = isOnline;
        List<Map<String, String>> dataForNextPost = new ArrayList<>();
        synchronized (postDataList) {
            while (dataForNextPost.size() < 100 && !postDataList.isEmpty()) {
                dataForNextPost.add(postDataList.remove(0));
            }
        }

        if (dataForNextPost.isEmpty()) {
            return;
        }

        try {
            if (!postData(dataForNextPost)) {
                isOnline = false;
                synchronized (postDataList) {
                    postDataList.addAll(dataForNextPost);
                }
            } else {
                isOnline = true;
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        if ((lastIsOnline == false) && (isOnline)) {
            loadFromBuffer();
        }

        if (isOnline == false && (postDataList.size() > maximumMessagesBeforeBuffer)) {
            List<Map<String, String>> dataToBuffer = new ArrayList<>();
            synchronized (postDataList) {
                dataToBuffer.addAll(postDataList);
                postDataList.clear();
            }
            saveToBuffer(dataToBuffer);
        }
    }

    private boolean postData(List<Map<String, String>> dataToPost) throws JsonProcessingException {
        if (dataToPost == null || dataToPost.isEmpty()) {
            return true;
        }

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

    private void saveToBuffer(List<Map<String, String>> toSave) {
        if (toSave == null || toSave.size() == 0) {
            return;
        }
        final Path bufferFile = bufferDirectory.resolve(System.currentTimeMillis() + ".txt");
        try (BufferedWriter writer = Files.newBufferedWriter(bufferFile, Charset.forName("UTF-8"))) {
            final String json = mapper.writeValueAsString(toSave);
            writer.write(json);
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
                final List<Map<String, String>> values = mapper.readValue(file, new TypeReference<List<Map<String, String>>>() {
                });
                postDataList.addAll(values);
                if (!file.delete()) {
                    System.out.println("Unable to delete buffer file: "+file.getPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
