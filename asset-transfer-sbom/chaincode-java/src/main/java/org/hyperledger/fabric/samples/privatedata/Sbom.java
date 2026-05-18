/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.privatedata;

import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import org.hyperledger.fabric.shim.ChaincodeException;
import org.json.JSONObject;

@Data
@AllArgsConstructor
@DataType()
public final class Sbom {

    @Property()
    private final String bomFormat;

    @Property()
    private final String specVersion;

    @Property()
    private final String serialNumber;

    @Property()
    private final int version;

    @Property()
    private final String metadata;

    @Property()
    private final String sbomHash;

    @Property()
    private String owner;


    public byte[] serialize() {
        String jsonStr = new JSONObject(this).toString();
        return jsonStr.getBytes(UTF_8);
    }

    public static Sbom deserialize(final byte[] assetJSON) {
        return deserialize(new String(assetJSON, UTF_8));
    }

    public static Sbom deserialize(final String assetJSON) {
        try {
            JSONObject json = new JSONObject(assetJSON);
            final String bomFormat = json.getString("bomFormat");
            final String specVersion = json.getString("specVersion");
            final String serialNumber = json.getString("serialNumber");
            final int version = json.getInt("version");
            final String metadata = json.getString("metadata");
            final String sbomHash = json.getString("sbomHash");            
            final String owner = json.getString("owner");
            return new Sbom(bomFormat, specVersion, serialNumber, version,metadata, sbomHash, owner);
        } catch (Exception e) {
            throw new ChaincodeException("Deserialize error: " + e.getMessage(), "DATA_ERROR");
        }
    }

}
