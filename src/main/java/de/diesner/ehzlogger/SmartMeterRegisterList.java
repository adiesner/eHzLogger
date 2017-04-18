package de.diesner.ehzlogger;

import java.util.ArrayList;
import java.util.List;

public class SmartMeterRegisterList {

    public List<SmartMeterRegister> getRegisterList() {
        return registerList;
    }

    private List<SmartMeterRegister> registerList = new ArrayList<>();

    public SmartMeterRegisterList() {
        initialize();
    }

    private void initialize() {
        //TODO: load from configuration file
        registerList.add(new SmartMeterRegister(new byte[]{(byte) 0x01, 0x00, 0x01, 0x08, 0x00, (byte) 0xFF}, "Wirkenergie_Total_Bezug")); // 1.8.0
        registerList.add(new SmartMeterRegister(new byte[]{(byte) 0x01, 0x00, 0x01, 0x08, 0x01, (byte) 0xFF}, "Wirkenergie_Tarif_1_Bezug")); // 1.8.1
        registerList.add(new SmartMeterRegister(new byte[]{(byte) 0x01, 0x00, 0x01, 0x08, 0x02, (byte) 0xFF}, "Wirkenergie_Tarif_2_Bezug")); // 1.8.2
        registerList.add(new SmartMeterRegister(new byte[]{(byte) 0x01, 0x00, 0x02, 0x08, 0x00, (byte) 0xFF}, "Wirkenergie_Total_Lieferung")); // 2.8.0
        registerList.add(new SmartMeterRegister(new byte[]{(byte) 0x01, 0x00, 0x02, 0x08, 0x01, (byte) 0xFF}, "Wirkenergie_Tarif_1_Lieferung")); // 2.8.1
        registerList.add(new SmartMeterRegister(new byte[]{(byte) 0x01, 0x00, 0x02, 0x08, 0x02, (byte) 0xFF}, "Wirkenergie_Tarif_2_Lieferung")); // 2.8.2
        registerList.add(new SmartMeterRegister(new byte[]{(byte) 0x01, 0x00, 0x10, 0x07, 0x00, (byte) 0xFF}, "Aktuelle_Gesamtwirkleistung")); // 16.7.0
    }

}
