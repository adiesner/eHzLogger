package de.diesner.ehzlogger;


public class SmartMeterRegister {
    private final byte obisHexCode[];
    private final String label;

    public SmartMeterRegister(byte[] obisHexCode, String label) {
        this.obisHexCode = obisHexCode.clone();
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }

    public boolean matches(byte [] obisCode) {
        for (int i=0; i<obisHexCode.length;i++) {
            if ((obisCode.length < i) || (obisCode[i] != obisHexCode[i])) {
                return false;
            }
        }
        return true;
    }
}
