/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;


import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import com.owlike.genson.Genson;
import org.json.JSONObject;

@Contract(
        name = "basic",
        info = @Info(
                title = "Asset Transfer",
                description = "The hyperlegendary asset transfer",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "a.transfer@example.com",
                        name = "Adrian Transfer",
                        url = "https://hyperledger.example.com")))
@Default
public final class AssetTransfer implements ContractInterface {

    private final Genson genson = new Genson();

    private enum AssetTransferErrors {
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS
    }

    /**
     * Creates some initial assets on the ledger.
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void InitLedger(final Context ctx) {
        putAsset(ctx, new Asset("CycloneDX", "1.6", "urn:uuid:cb8eb8c0-aa63-4a25-a495-6eaa8dc4a241", 1, "\"manufacture\": {\"name\": \"Hanwha Vision Co., Ltd.\",\"url\":[\"https://www.hanwhavision.com\"]}",
                "dummy", "  [    {      \"name\": \"pjsip\",      \"bom-ref\": \"pjsip\",      \"type\": \"library\",      \"version\": \"2.14.1\",      \"licenses\": [        {          \"license\": {            \"name\": \"PJSIP Commercial License\"} } , \"description\": \"pjsip is a professionally supported open source comprehensive multimedia communication library based on the SIP protocol. It is integrated with a rich media and a NAT traversal library supporting the ICE protocol. It is very portable and has a small footprint for embedded use.\",      \"supplier\": {        \"url\": [          \"https://www.pjsip.org/\"        ] } }]","gh"));
        putAsset(ctx, new Asset("CycloneDX", "1.6", "urn:uuid:cb8eb8c0-aa63-4a25-a495-6eaa8dc4a242", 2, "\"manufacture\": {\"name\": \"Hanwha Vision Co., Ltd.\",\"url\":[\"https://www.hanwhavision.com\"]}",
                "dummy",  "  [    {      \"name\": \"pjsip\",      \"bom-ref\": \"pjsip\",      \"type\": \"library\",      \"version\": \"2.14.1\",      \"licenses\": [        {          \"license\": {            \"name\": \"PJSIP Commercial License\"}}], \"description\": \"pjsip is a professionally supported open source comprehensive multimedia communication library based on the SIP protocol. It is integrated with a rich media and a NAT traversal library supporting the ICE protocol. It is very portable and has a small footprint for embedded use.\",      \"supplier\": {        \"url\": [          \"https://www.pjsip.org/\"        ] } }]","gh"));
        putAsset(ctx, new Asset("CycloneDX", "1.6", "urn:uuid:cb8eb8c0-aa63-4a25-a495-6eaa8dc4a243", 3, "\"manufacture\": {\"name\": \"Hanwha Vision Co., Ltd.\",\"url\":[\"https://www.hanwhavision.com\"]}",
                "dummy",  "  [    {      \"name\": \"pjsip\",      \"bom-ref\": \"pjsip\",      \"type\": \"library\",      \"version\": \"2.14.1\",      \"licenses\": [        {          \"license\": {            \"name\": \"PJSIP Commercial License\" }}], \"description\": \"pjsip is a professionally supported open source comprehensive multimedia communication library based on the SIP protocol. It is integrated with a rich media and a NAT traversal library supporting the ICE protocol. It is very portable and has a small footprint for embedded use.\",      \"supplier\": {        \"url\": [          \"https://www.pjsip.org/\"        ] } }]","gh"));

    }

    public String encrypt(String text) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(text.getBytes());

        return bytesToHex(md.digest());
    }

    private String bytesToHex(byte[] digest) {
        StringBuilder builder = new StringBuilder();
        for (byte b : digest){
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }


    /**
     * Creates a new asset on the ledger.
     *
     * @param ctx the transaction context
     * @return the created asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset CreateAsset(final Context ctx, final String base64Asset ) throws NoSuchAlgorithmException {
        String assetJson = new String(Base64.getDecoder().decode(base64Asset), StandardCharsets.UTF_8);
        JSONObject json = new JSONObject(assetJson);
        System.out.println("json : "+assetJson);
        final String bomFormat;
        final String specVersion;
        final String serialNumber;
        final Integer version;
        final String metadata;
        final String sbomHash;
        final String components;
        try{
            bomFormat = json.getString("bomFormat");
            specVersion = json.getString("specVersion");
            serialNumber = json.getString("serialNumber");
            version = json.getInt("version");
            metadata =json.get("metadata").toString();
            sbomHash = encrypt(json.toString());
            components = json.get("components").toString();
        }catch (Exception e){
            String errorMessage = String.format("TransientMap deserialized error: %s", e);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, "INCOMPLETE_INPUT");

        }


        Asset asset = new Asset(bomFormat,specVersion,serialNumber,version,metadata,sbomHash,components,"");
        if (AssetExists(ctx, asset.getSerialNumber())) {
            String errorMessage = String.format("Asset %s already exists", asset.getSerialNumber());
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }
        String clientID = ctx.getClientIdentity().getId();
        asset.setOwner(clientID);

        System.out.println(asset);
        return putAsset(ctx, asset);
    }

    private Asset putAsset(final Context ctx, final Asset asset) {
        // Use Genson to convert the Asset into string, sort it alphabetically and serialize it into a json string
        String sortedJson = genson.serialize(asset);
        ctx.getStub().putStringState(asset.getSerialNumber(), sortedJson);

        return asset;
    }

    /**
     * Retrieves an asset with the specified ID from the ledger.
     *
     * @param ctx the transaction context
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Asset ReadAsset(final Context ctx, final String serialNumber) {
        String assetJSON = ctx.getStub().getStringState(serialNumber);

        if (assetJSON == null || assetJSON.isEmpty()) {
            String errorMessage = String.format("Sbom %s does not exist", serialNumber);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        return genson.deserialize(assetJSON, Asset.class);
    }

//    /**
//     * Updates the properties of an asset on the ledger.
//     *
//     * @param ctx the transaction context
//     * @param assetID the ID of the asset being updated
//     * @param color the color of the asset being updated
//     * @param size the size of the asset being updated
//     * @param owner the owner of the asset being updated
//     * @param appraisedValue the appraisedValue of the asset being updated
//     * @return the transferred asset
//     */
//    @Transaction(intent = Transaction.TYPE.SUBMIT)
//    public Asset UpdateAsset(final Context ctx, final String assetID, final String color, final int size,
//        final String owner, final int appraisedValue) {
//
//        if (!AssetExists(ctx, assetID)) {
//            String errorMessage = String.format("Asset %s does not exist", assetID);
//            System.out.println(errorMessage);
//            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
//        }
//
//        return putAsset(ctx, new Asset(assetID, color, size, owner, appraisedValue));
//    }

    /**
     * Deletes asset on the ledger.
     *
     * @param ctx the transaction context
     *
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAsset(final Context ctx, final String serialNumber) {
        if (!AssetExists(ctx, serialNumber)) {
            String errorMessage = String.format("Sbom %s does not exist", serialNumber);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        ctx.getStub().delState(serialNumber);
    }

    /**
     * Checks the existence of the asset on the ledger
     *
     * @param ctx the transaction context
     * @param assetID the ID of the asset
     * @return boolean indicating the existence of the asset
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean AssetExists(final Context ctx, final String assetID) {
        String assetJSON = ctx.getStub().getStringState(assetID);

        return (assetJSON != null && !assetJSON.isEmpty());
    }

//    /**
//     * Changes the owner of a asset on the ledger.
//     *
//     * @param ctx the transaction context
//     * @param assetID the ID of the asset being transferred
//     * @param newOwner the new owner
//     * @return the old owner
//     */
//    @Transaction(intent = Transaction.TYPE.SUBMIT)
//    public String TransferAsset(final Context ctx, final String assetID, final String newOwner) {
//        String assetJSON = ctx.getStub().getStringState(assetID);
//
//        if (assetJSON == null || assetJSON.isEmpty()) {
//            String errorMessage = String.format("Asset %s does not exist", assetID);
//            System.out.println(errorMessage);
//            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
//        }
//
//        Asset asset = genson.deserialize(assetJSON, Asset.class);
//
//        putAsset(ctx, new Asset(asset.getAssetID(), asset.getColor(), asset.getSize(), newOwner, asset.getAppraisedValue()));
//
//        return asset.getOwner();
//    }

    /**
     * Retrieves all assets from the ledger.
     *
     * @param ctx the transaction context
     * @return array of assets found on the ledger
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllAssets(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        List<Asset> queryResults = new ArrayList<>();

        // To retrieve all assets from the ledger use getStateByRange with empty startKey & endKey.
        // Giving empty startKey & endKey is interpreted as all the keys from beginning to end.
        // As another example, if you use startKey = 'asset0', endKey = 'asset9' ,
        // then getStateByRange will retrieve asset with keys between asset0 (inclusive) and asset9 (exclusive) in lexical order.
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");

        for (KeyValue result: results) {
            Asset asset = genson.deserialize(result.getStringValue(), Asset.class);
            System.out.println(asset);
            queryResults.add(asset);
        }

        return genson.serialize(queryResults);
    }
}
