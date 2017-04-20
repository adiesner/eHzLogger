package de.diesner.ehzlogger;

import lombok.Getter;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class SmartMeterRegisterList {

    @Getter
    private List<SmartMeterRegister> registerList = new ArrayList<>();

    public SmartMeterRegisterList(@NonNull Properties properties) {
        initialize(properties);
    }

    private void initialize(Properties properties) {
        for (Map.Entry<Object, Object> property : properties.entrySet()) {
            String key = (String)property.getKey();
            if (key.startsWith("register.")) {
                byte[] obisHexCode = hexStringToByteArray(key.substring(9));
                registerList.add(new SmartMeterRegister(obisHexCode, (String)property.getValue()));
            }
        }
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

}
