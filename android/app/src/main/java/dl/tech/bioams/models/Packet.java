package dl.tech.bioams.models;

import java.io.Serializable;

public class Packet implements Serializable {
    public byte packetType = 0;
    public byte[] packetPayload = {};
}