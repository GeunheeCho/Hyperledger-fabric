package org.hyperledger.fabric.samples.privatedata;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import org.hyperledger.fabric.shim.ChaincodeException;
import org.json.JSONObject;

import static java.nio.charset.StandardCharsets.UTF_8;

@DataType()
@Data
@AllArgsConstructor
public final class SbomPrivate {

    @Property()
    private final String serialNumber;

   @Property()
    private final String components;


    public byte[] serialize() {
        String jsonStr = new JSONObject(this).toString();
        return jsonStr.getBytes(UTF_8);
    }

    public static SbomPrivate deserialize(final byte[] assetJSON) {
        try {
            JSONObject json = new JSONObject(new String(assetJSON, UTF_8));
            final String serialNumber = json.getString("serialNumber");
            final String components = json.getString("components");
            return new SbomPrivate(serialNumber, components);
        } catch (Exception e) {
            throw new ChaincodeException("Deserialize error: " + e.getMessage(), "DATA_ERROR");
        }
    }


}
