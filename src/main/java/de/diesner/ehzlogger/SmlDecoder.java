package de.diesner.ehzlogger;

import org.openmuc.jsml.structures.*;

public class SmlDecoder {

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
}
