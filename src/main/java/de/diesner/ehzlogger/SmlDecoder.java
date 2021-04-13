package de.diesner.ehzlogger;

import org.openmuc.jsml.structures.ASNObject;
import org.openmuc.jsml.structures.Integer16;
import org.openmuc.jsml.structures.Integer32;
import org.openmuc.jsml.structures.Integer64;
import org.openmuc.jsml.structures.Integer8;
import org.openmuc.jsml.structures.SML_GetListRes;
import org.openmuc.jsml.structures.SML_List;
import org.openmuc.jsml.structures.SML_ListEntry;
import org.openmuc.jsml.structures.SML_Message;
import org.openmuc.jsml.structures.SML_MessageBody;
import org.openmuc.jsml.structures.SML_Unit;
import org.openmuc.jsml.structures.SML_Value;
import org.openmuc.jsml.structures.Unsigned16;
import org.openmuc.jsml.structures.Unsigned32;
import org.openmuc.jsml.structures.Unsigned64;
import org.openmuc.jsml.structures.Unsigned8;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SmlDecoder {

    private static DecimalFormat df = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));

    public static Long decodeASN(ASNObject obj) {
        if (obj.getClass().equals(Integer8.class)) {
            return (long) ((Integer8) obj).getVal();
        } else if (obj.getClass().equals(Integer16.class)) {
            return (long) ((Integer16) obj).getVal();
        } else if (obj.getClass().equals(Integer32.class)) {
            return (long) ((Integer32) obj).getVal();
        } else if (obj.getClass().equals(Integer64.class)) {
            return ((Integer64)obj).getVal();
        } else if (obj.getClass().equals(Unsigned8.class)) {
            return (long) ((Unsigned8)obj).getVal();
        } else if (obj.getClass().equals(Unsigned16.class)) {
            return (long) ((Unsigned16)obj).getVal();
        } else if (obj.getClass().equals(Unsigned32.class)) {
            return (long) ((Unsigned32)obj).getVal();
        } else if (obj.getClass().equals(Unsigned64.class)) {
            return ((Unsigned64)obj).getVal();
        }
        return null;
    }

    public static Map<String, String> extractValues(List<SML_Message> messageList, SmartMeterRegisterList smartMeterRegisterList) {
        df.setMaximumFractionDigits(340); // 340 = DecimalFormat.DOUBLE_FRACTION_DIGITS

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
                                values.put(register.getLabel(), df.format(numericalValue / 10.0));
                                break;
                            }
                        }
                    }
                }
            }
        }
        return values;
    }

}
