package de.diesner.ehzlogger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

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

        client = ClientBuilder.newBuilder()
            .connectTimeout(1000, TimeUnit.MILLISECONDS)
            .readTimeout(5000, TimeUnit.MILLISECONDS)
            .build();
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
        final WebTarget target = client.target(remoteUri);
        final Response response;
        try {
             response = target.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.entity(json, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (response.getStatus() > 204) {
            System.out.println("Failed : HTTP error code : " + response.getStatus());
            if (response.hasEntity()) {
                System.out.println(response.getEntity());
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
            if (!file.isFile()) {
                continue;
            }
            try {
                final List<Map<String, String>> values = mapper.readValue(file, new TypeReference<List<Map<String, String>>>() {
                });
                postDataList.addAll(values);
            } catch (IOException e) {
                System.out.println("Error loading file: "+file.getPath());
                e.printStackTrace();
            } finally {
                if (!file.delete()) {
                    System.out.println("Unable to delete buffer file: "+file.getPath());
                }
            }
        }
    }

}
