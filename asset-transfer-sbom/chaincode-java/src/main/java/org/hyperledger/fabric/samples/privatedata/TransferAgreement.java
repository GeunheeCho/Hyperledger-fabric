package org.hyperledger.fabric.samples.privatedata;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.json.JSONObject;

import static java.nio.charset.StandardCharsets.UTF_8;

@DataType()
public final class TransferAgreement {

    @Property()
    private final String serialNumber;


    @Property()
    private String buyerID;

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getBuyerID() {
        return buyerID;
    }

    public TransferAgreement(final String serialNumber,
                             final String buyer) {
        this.serialNumber = serialNumber;
        this.buyerID = buyer;
    }

    public byte[] serialize() {
        String jsonStr = new JSONObject(this).toString();
        return jsonStr.getBytes(UTF_8);
    }

    public static TransferAgreement deserialize(final byte[] assetJSON) {
        try {
            JSONObject json = new JSONObject(new String(assetJSON, UTF_8));
            final String serialNumber = json.getString("serialNumber");
            final String buyerID = json.getString("buyerID");
            return new TransferAgreement(serialNumber, buyerID);
        } catch (Exception e) {
            throw new ChaincodeException("Deserialize error: " + e.getMessage(), "DATA_ERROR");
        }
    }


}
