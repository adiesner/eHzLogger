package de.diesner.ehzlogger;

import org.openmuc.jsml.structures.SML_Message;

import java.util.List;

public interface SmlForwarder {
    void messageReceived(List<SML_Message> messageList);
}
