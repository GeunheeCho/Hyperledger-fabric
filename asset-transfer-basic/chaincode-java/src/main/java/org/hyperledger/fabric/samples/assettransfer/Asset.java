/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import static java.nio.charset.StandardCharsets.UTF_8;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import org.hyperledger.fabric.shim.ChaincodeException;
import org.json.JSONObject;

@Data
@AllArgsConstructor
@NoArgsConstructor
@DataType()
public final class Asset {


    @Property()
    private String bomFormat;

    @Property()
    private String specVersion;

    @Property()
    private String serialNumber;

    @Property()
    private int version;

    @Property()
    private String metadata;

    @Property()
    private String sbomHash;

    @Property()
    private String components;

    @Property()
    private String owner;

    public byte[] serialize() {
        String jsonStr = new JSONObject(this).toString();
        return jsonStr.getBytes(UTF_8);
    }

    public static Asset deserialize(final byte[] assetJSON) {
        return deserialize(new String(assetJSON, UTF_8));
    }

    public static Asset deserialize(final String assetJSON) {
        try {
            JSONObject json = new JSONObject(assetJSON);
            final String bomFormat = json.getString("bomFormat");
            final String specVersion = json.getString("specVersion");
            final String serialNumber = json.getString("serialNumber");
            final int version = json.getInt("version");
            final String metadata = json.getString("metadata");
            final String sbomHash = json.getString("sbomHash");
            final String components = json.getString("components");
            final String owner = json.getString("owner");
            return new Asset(bomFormat, specVersion, serialNumber, version,metadata, sbomHash, components, owner);
        } catch (Exception e) {
            throw new ChaincodeException("Deserialize error: " + e.getMessage(), "DATA_ERROR");
        }
    }

}
