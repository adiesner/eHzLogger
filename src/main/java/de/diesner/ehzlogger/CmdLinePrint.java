package de.diesner.ehzlogger;

import org.openmuc.jsml.structures.*;

import java.util.List;

public class CmdLinePrint implements SmlForwarder {

    private final SmartMeterRegisterList smartMeterRegisterList;

    public CmdLinePrint(SmartMeterRegisterList smartMeterRegisterList) {
        this.smartMeterRegisterList = smartMeterRegisterList;
    }

    private static String serverIdToString(OctetString serverId) {
        StringBuilder result = new StringBuilder();

        for (byte b : serverId.getOctetString()) {
            result.append(String.format("%02X:", b));
        }

        result.deleteCharAt(result.length() - 1);

        return result.toString();
    }

    public void messageReceived(List<SML_Message> messageList) {
        for (int i = 0; i < messageList.size(); i++) {
            SML_Message sml_message = messageList.get(i);
            int tag = sml_message.getMessageBody().getTag().getVal();
            if (tag == SML_MessageBody.GetListResponse) {
                SML_GetListRes resp = (SML_GetListRes) sml_message.getMessageBody().getChoice();
                SML_List smlList = resp.getValList();

                System.out.print("Server-ID: ");
                System.out.println(serverIdToString(resp.getServerId()));

                SML_ListEntry[] list = smlList.getValListEntry();

                for (SML_ListEntry entry : list) {
                    int unit = entry.getUnit().getVal();
                    String unitName;
                    // Only handle entries with meaningful units
                    switch (unit) {
                        case SML_Unit.WATT:
                            unitName = "W";
                            break;
                        case SML_Unit.WATT_HOUR:
                            unitName = "Wh";
                            break;
                        default:
                            unitName = null;
                    }
                    if (unitName != null) {
                        SML_Value value = entry.getValue();
                        Long numericalValue = SmlDecoder.decodeASN(value.getChoice());
                        if (numericalValue == null) {
                            System.out.println("Got non-numerical value for an energy measurement. Skipping.");
                            continue;
                        }

                        byte objNameBytes[] = entry.getObjName().getOctetString();
                        String registerLabel = "?";
                        for (SmartMeterRegister register : smartMeterRegisterList.getRegisterList()) {
                            if (register.matches(objNameBytes)) {
                                registerLabel = register.getLabel();
                                break;
                            }
                        }

                        // We need to force Java to treat the bytes as unsigned integers by AND-ing them with 0xFF
                        System.out.printf("%d-%d:%d.%d.%d*%d = %.1f %s (%s)%n",
                                0xFF & objNameBytes[0], 0xFF & objNameBytes[1], 0xFF & objNameBytes[2],
                                0xFF & objNameBytes[3], 0xFF & objNameBytes[4], 0xFF & objNameBytes[5],
                                numericalValue / 10.0,
                                unitName, registerLabel);
                    }
                }
            }
        }
        System.out.println();
    }
}
