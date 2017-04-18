package de.diesner.ehzlogger;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.LoggingFilter;
import org.openmuc.jsml.structures.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfluxDbForward implements SmlForwarder {

    private final String remoteUri;
    private final String measurement;
    private final Client client;
    private final SmartMeterRegisterList smartMeterRegisterList;

    public InfluxDbForward(String remoteUri, String measurement, SmartMeterRegisterList smartMeterRegisterList) {
        this.remoteUri = remoteUri;
        this.measurement = measurement;
        this.smartMeterRegisterList = smartMeterRegisterList;
        ClientConfig clientConfig = new DefaultClientConfig();
        client = Client.create(clientConfig);
    }

    public void enableHttpDebug() {
        client.addFilter(new LoggingFilter(System.out));
    }

    @Override
    public void messageReceived(List<SML_Message> messageList) {
        Map<String, String> values = extractValues(messageList);
        postData(values);
    }

    private Map<String, String> extractValues(List<SML_Message> messageList) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < messageList.size(); i++) {
            SML_Message sml_message = messageList.get(i);
            int tag = sml_message.getMessageBody().getTag().getVal();
            if (tag == SML_MessageBody.GetListResponse) {
                SML_GetListRes resp = (SML_GetListRes) sml_message.getMessageBody().getChoice();
                SML_List smlList = resp.getValList();

                SML_ListEntry[] list = smlList.getValListEntry();

                for (SML_ListEntry entry : list) {
                    int unit = entry.getUnit().getVal();

                    if (unit == SML_Unit.WATT || unit == SML_Unit.WATT_HOUR) {
                        SML_Value value = entry.getValue();
                        Long numericalValue = SmlDecoder.decodeASN(value.getChoice());
                        if (numericalValue == null) {
                            System.out.println("Got non-numerical value for an energy measurement. Skipping.");
                            continue;
                        }

                        byte objNameBytes[] = entry.getObjName().getOctetString();
                        for (SmartMeterRegister register : smartMeterRegisterList.getRegisterList()) {
                            if (register.matches(objNameBytes)) {
                                values.put(register.getLabel(), String.valueOf(numericalValue / 10.0));
                                break;
                            }
                        }
                    }
                }
            }
        }
        return values;
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

    private void postData(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        WebResource webResource = client.resource(remoteUri);

        String postData = toLineProtocol(measurement, values);

        ClientResponse response = webResource.post(ClientResponse.class, postData);

        if (response.getStatus() != 204) {
            System.out.println("Failed : HTTP error code : " + response.getStatus());
            if (response.hasEntity()) {
                System.out.println(response.getEntity(String.class));
            }
        }
    }

}
