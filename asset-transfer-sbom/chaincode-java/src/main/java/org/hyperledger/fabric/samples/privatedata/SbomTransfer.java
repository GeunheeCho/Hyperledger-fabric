/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.privatedata;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.Chaincode;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;

import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.json.JSONObject;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main Chaincode class. A ContractInterface gets converted to Chaincode internally.
 * @see Chaincode
 *
 * Each chaincode transaction function must take, Context as first parameter.
 * Unless specified otherwise via annotation (@Contract or @Transaction), the contract name
 * is the class name (without package)
 * and the transaction name is the method name.
 *
 * To create fabric test-network
 *   cd fabric-samples/test-network
 *   ./network.sh up createChannel -ca -s couchdb
 * To deploy this chaincode to test-network, use the collection config as described in
 * See <a href="https://hyperledger-fabric.readthedocs.io/en/latest/private_data_tutorial.html"</a>
 * Change both -ccs sequence & -ccv version args for iterative deployment
 *  ./network.sh deployCC -ccn sbom -ccp ../asset-transfer-sbom/chaincode-java/ -ccl java -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -cccg ../asset-transfer-sbom/chaincode-java/collections_config.json -ccs 1 -ccv 1
 * export $(./setOrgEnv.sh Org1 | xargs)
 * export ASSET_PROPS=$(cat sbomTest.json | tr -d '\r\n' | base64 | tr -d '\r\n')
 * peer chaincode invoke -o localhost:7050   --ordererTLSHostnameOverride orderer.example.com   --tls   --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/tls/ca.crt"   -C mychannel -n sbom   -c '{"function":"CreateAsset","Args":[]}'   --transient "{\"sbom_property\":\"$ASSET_PROPS\"}"
 *  peer chaincode query -C mychannel -n sbom -c '{"Args":["ReadAsset", "urn:uuid:cb8eb8c0-aa63-4a25-a495-6eaa8dc4a243"]}'
 */
@Contract(
        name = "sbom",
        info = @Info(
                title = "Sbom Transfer Private Data",
                description = "The hyperlegendary asset transfer private data",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "a.transfer@example.com",
                        name = "Private Transfer",
                        url = "https://hyperledger.example.com")))
@Default
public final class SbomTransfer implements ContractInterface {

    static final String ASSET_COLLECTION_NAME = "assetCollection";
    static final String AGREEMENT_KEYPREFIX = "transferAgreement";

    private enum AssetTransferErrors {
        INCOMPLETE_INPUT,
        INVALID_ACCESS,
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS
    }

    /**
     * Retrieves the asset public details with the specified ID from the AssetCollection.
     *
     * @param ctx     the transaction context
     * @param serialNumber the ID of the asset
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Sbom ReadAsset(final Context ctx, final String serialNumber) {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("ReadAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, serialNumber);
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, serialNumber);

        if (assetJSON == null || assetJSON.length == 0) {
            System.out.printf("Asset not found: ID %s\n", serialNumber);
            return null;
        }

        Sbom sbom = Sbom.deserialize(assetJSON);
        return sbom;
    }

    /**
     * Retrieves the asset's AssetPrivateDetails details with the specified ID from the Collection.
     *
     * @param ctx        the transaction context
     * @param collection the org's collection containing asset private details
     * @param serialNumber    the ID of the asset
     * @return the AssetPrivateDetails from the collection, if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public SbomPrivate ReadSbomPrivate(final Context ctx, final String collection, final String serialNumber) {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("ReadAssetPrivateDetails: collection %s, ID %s\n", collection, serialNumber);
        byte[] assetPrvJSON = stub.getPrivateData(collection, serialNumber);

        if (assetPrvJSON == null || assetPrvJSON.length == 0) {
            String errorMessage = String.format("AssetPrivateDetails %s does not exist in collection %s", serialNumber, collection);
            System.out.println(errorMessage);
            return null;
        }

        SbomPrivate sbomp = SbomPrivate.deserialize(assetPrvJSON);
        return sbomp;
    }

    /**
     * ReadTransferAgreement gets the buyer's identity from the transfer agreement from collection
     *
     * @param ctx     the transaction context
     * @param serialNumber the ID of the asset
     * @return the AssetPrivateDetails from the collection, if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public TransferAgreement ReadTransferAgreement(final Context ctx, final String serialNumber) {
        ChaincodeStub stub = ctx.getStub();

        CompositeKey aggKey = stub.createCompositeKey(AGREEMENT_KEYPREFIX, serialNumber);
        System.out.printf("ReadTransferAgreement Get: collection %s, ID %s, Key %s\n", ASSET_COLLECTION_NAME, serialNumber, aggKey);
        byte[] buyerIdentity = stub.getPrivateData(ASSET_COLLECTION_NAME, aggKey.toString());

        if (buyerIdentity == null || buyerIdentity.length == 0) {
            String errorMessage = String.format("BuyerIdentity for asset %s does not exist in TransferAgreement ", serialNumber);
            System.out.println(errorMessage);
            return null;
        }

        return new TransferAgreement(serialNumber, new String(buyerIdentity, UTF_8));
    }

    /**
     * GetAssetByRange performs a range query based on the start and end keys provided. Range
     * queries can be used to read data from private data collections, but can not be used in
     * a transaction that also writes to private collection, since transaction may not get endorsed
     * on some peers that do not have the collection.
     *
     * @param ctx      the transaction context
     * @param startKey for ID range of the asset
     * @param endKey   for ID range of the asset
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Sbom[] GetAssetByRange(final Context ctx, final String startKey, final String endKey) throws Exception {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("GetAssetByRange: start %s, end %s\n", startKey, endKey);

        List<Sbom> queryResults = new ArrayList<>();
        // retrieve asset with keys between startKey (inclusive) and endKey(exclusive) in lexical order.
        try (QueryResultsIterator<KeyValue> results = stub.getPrivateDataByRange(ASSET_COLLECTION_NAME, startKey, endKey)) {
            for (KeyValue result : results) {
                if (result.getStringValue() == null || result.getStringValue().length() == 0) {
                    System.err.printf("Invalid Asset json: %s\n", result.getStringValue());
                    continue;
                }
                Sbom sbom = Sbom.deserialize(result.getStringValue());
                queryResults.add(sbom);
                System.out.println("QueryResult: " + sbom.toString());
            }
        }
        return queryResults.toArray(new Sbom[0]);
    }

    // =======Rich queries =========================================================================
    // Two examples of rich queries are provided below (parameterized query and ad hoc query).
    // Rich queries pass a query string to the state database.
    // Rich queries are only supported by state database implementations
    //  that support rich query (e.g. CouchDB).
    // The query string is in the syntax of the underlying state database.
    // With rich queries there is no guarantee that the result set hasn't changed between
    //  endorsement time and commit time, aka 'phantom reads'.
    // Therefore, rich queries should not be used in update transactions, unless the
    // application handles the possibility of result set changes between endorsement and commit time.
    // Rich queries can be used for point-in-time queries against a peer.
    // ============================================================================================

    /**
     * QueryAssetByOwner queries for assets based on assetType, owner.
     * This is an example of a parameterized query where the query logic is baked into the chaincode,
     * and accepting a single query parameter (owner).
     *
     * @param ctx       the transaction context
     * @param assetType type to query for
     * @param owner     asset owner to query for
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Sbom[] QueryAssetByOwner(final Context ctx, final String assetType, final String owner) throws Exception {
        String queryString = String.format("{\"selector\":{\"objectType\":\"%s\",\"owner\":\"%s\"}}", assetType, owner);
        return getQueryResult(ctx, queryString);
    }

    /**
     * QueryAssets uses a query string to perform a query for assets.
     * Query string matching state database syntax is passed in and executed as is.
     * Supports ad hoc queries that can be defined at runtime by the client.
     *
     * @param ctx         the transaction context
     * @param queryString query string matching state database syntax
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Sbom[] QueryAssets(final Context ctx, final String queryString) throws Exception {
        return getQueryResult(ctx, queryString);
    }

    private Sbom[] getQueryResult(final Context ctx, final String queryString) throws Exception {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("QueryAssets: %s\n", queryString);

        List<Sbom> queryResults = new ArrayList<Sbom>();
        // retrieve asset with keys between startKey (inclusive) and endKey(exclusive) in lexical order.
        try (QueryResultsIterator<KeyValue> results = stub.getPrivateDataQueryResult(ASSET_COLLECTION_NAME, queryString)) {
            for (KeyValue result : results) {
                if (result.getStringValue() == null || result.getStringValue().length() == 0) {
                    System.err.printf("Invalid Asset json: %s\n", result.getStringValue());
                    continue;
                }
                Sbom asset = Sbom.deserialize(result.getStringValue());
                queryResults.add(asset);
                System.out.println("QueryResult: " + asset.toString());
            }
        }
        return queryResults.toArray(new Sbom[0]);
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
     * Creates a new asset on the ledger from asset properties passed in as transient map.
     * Asset owner will be inferred from the ClientId via stub api
     *
     * @param ctx the transaction context
     *            Transient map with asset_properties key with asset json as value
     * @return the created asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT) //쓰기 가능
    public Sbom CreateAsset(final Context ctx)  throws NoSuchAlgorithmException{
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient(); // 비밀채널로 입력받기
        if (!transientMap.containsKey("sbom_property")) {
            String errorMessage = String.format("CreateAsset call must specify sbom_property in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("sbom_property"); //asset_properties의 value 값 get
        final String bomFormat;
        final String specVersion;
        final String serialNumber;
        final Integer version;
        final String metadata;
        final String sbomHash;
        String components = "";
        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8)); //value를 json으로
//            Map<String, Object> tMap = json.toMap(); //json을 맵핑
            bomFormat = json.getString("bomFormat");
            specVersion = json.getString("specVersion");
            serialNumber = json.getString("serialNumber");
            version = json.getInt("version");
            metadata =json.get("metadata").toString();
            sbomHash = encrypt(json.toString());
            if (json.has("components")) {
                components = json.get("components").toString();
            }
        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error: %s", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        //유효성검사
        String errorMessage = null;
        if (serialNumber.equals("")) {
            errorMessage = String.format("Empty input in Transient map: serialNumber");
        }
        if (sbomHash.equals("")) {
            errorMessage = String.format("Empty input in Transient map: sbomHash");
        }
        if (metadata.equals("")) {
            errorMessage = String.format("Empty input in Transient map: metadata");
        }
        if (components.equals("")) {
            errorMessage = String.format("Empty input in Transient map: components");
        }
        if (errorMessage != null) {
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        String clientID = ctx.getClientIdentity().getId();
        Sbom sbom = new Sbom(bomFormat, specVersion, serialNumber, version, metadata, sbomHash, clientID);
        // sbom 이미 있는지 확인
        byte[] assetJSON = ctx.getStub().getPrivateData(ASSET_COLLECTION_NAME, serialNumber);
        if (assetJSON != null && assetJSON.length > 0) { //privatedata 중복확인
            errorMessage = String.format("Asset %s already exists", serialNumber);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }

        
        // Verify that the client is submitting request to peer in their organization
        // This is to ensure that a client from another org doesn't attempt to read or
        // write private data from this peer.
        // verifyClientOrgMatchesPeerOrg(ctx);

    
        System.out.printf("CreateAsset Put: collection %s, ID %s\n", ASSET_COLLECTION_NAME, serialNumber);
        System.out.printf("Put: collection %s, ID %s\n", ASSET_COLLECTION_NAME, new String(sbom.serialize()));
        stub.putPrivateData(ASSET_COLLECTION_NAME, serialNumber, sbom.serialize());

        // Get collection name for this organization.
        String orgCollectionName = getCollectionName(ctx);

        // Save AssetPrivateDetails to org collection
        SbomPrivate sbomP = new SbomPrivate(serialNumber, components);
        System.out.printf("Put AssetPrivateDetails: collection %s, ID %s\n", orgCollectionName, serialNumber);
        stub.putPrivateData(orgCollectionName, serialNumber, sbomP.serialize());

        return sbom;
    }

    // /**
    //  * AgreeToTransfer is used by the potential buyer of the asset to agree to the
    //  * asset value. The agreed to appraisal value is stored in the buying orgs
    //  * org specifc collection, while the buyer client ID is stored in the asset collection
    //  * using a composite key
    //  * Uses transient map with key asset_value
    //  *
    //  * @param ctx the transaction context
    //  */
    // @Transaction(intent = Transaction.TYPE.SUBMIT)
    // public void AgreeToTransfer(final Context ctx) {
    //     ChaincodeStub stub = ctx.getStub();
    //     Map<String, byte[]> transientMap = ctx.getStub().getTransient();
    //     if (!transientMap.containsKey("asset_value")) {
    //         String errorMessage = String.format("AgreeToTransfer call must specify \"asset_value\" in Transient map input");
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     byte[] transientAssetJSON = transientMap.get("asset_value");
    //     AssetPrivateDetails assetPriv;
    //     String serialNumber;
    //     try {
    //         JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
    //         serialNumber = json.getString("serialNumber");
    //         final int appraisedValue = json.getInt("appraisedValue");

    //         assetPriv = new AssetPrivateDetails(serialNumber, appraisedValue);
    //     } catch (Exception err) {
    //         String errorMessage = String.format("TransientMap deserialized error %s ", err);
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     if (assetID.equals("")) {
    //         String errorMessage = String.format("Invalid input in Transient map: serialNumber");
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }
    //     if (assetPriv.getAppraisedValue() <= 0) { // appraisedValue field must be a positive integer
    //         String errorMessage = String.format("Input must be positive integer: appraisedValue");
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }
    //     System.out.printf("AgreeToTransfer: verify asset %s exists\n", serialNumber);
    //     Asset existing = ReadAsset(ctx, serialNumber);
    //     if (existing == null) {
    //         String errorMessage = String.format("Asset does not exist in the collection: ", serialNumber);
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }
    //     // Get collection name for this organization.
    //     String orgCollectionName = getCollectionName(ctx);

    //     verifyClientOrgMatchesPeerOrg(ctx);

    //     // Save AssetPrivateDetails to org collection
    //     System.out.printf("Put AssetPrivateDetails: collection %s, ID %s\n", orgCollectionName, serialNumber);
    //     stub.putPrivateData(orgCollectionName, serialNumber, assetPriv.serialize());

    //     String clientID = ctx.getClientIdentity().getId();
    //     // Write the AgreeToTransfer key in assetCollection
    //     CompositeKey aggKey = stub.createCompositeKey(AGREEMENT_KEYPREFIX, serialNumber);
    //     System.out.printf("AgreeToTransfer Put: collection %s, ID %s, Key %s\n", ASSET_COLLECTION_NAME, serialNumber, aggKey);
    //     stub.putPrivateData(ASSET_COLLECTION_NAME, aggKey.toString(), clientID);
    // }

    // /**
    //  * TransferAsset transfers the asset to the new owner by setting a new owner ID based on
    //  * AgreeToTransfer data
    //  *
    //  * @param ctx the transaction context
    //  * @return none
    //  */
    // @Transaction(intent = Transaction.TYPE.SUBMIT)
    // public void TransferAsset(final Context ctx) {
    //     ChaincodeStub stub = ctx.getStub();
    //     Map<String, byte[]> transientMap = ctx.getStub().getTransient();
    //     if (!transientMap.containsKey("asset_owner")) {
    //         String errorMessage = "TransferAsset call must specify \"asset_owner\" in Transient map input";
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     byte[] transientAssetJSON = transientMap.get("asset_owner");
    //     final String assetID;
    //     final String buyerMSP;
    //     try {
    //         JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
    //         assetID = json.getString("assetID");
    //         buyerMSP = json.getString("buyerMSP");

    //     } catch (Exception err) {
    //         String errorMessage = String.format("TransientMap deserialized error %s ", err);
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     if (assetID.equals("")) {
    //         String errorMessage = String.format("Invalid input in Transient map: " + "assetID");
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }
    //     if (buyerMSP.equals("")) {
    //         String errorMessage = String.format("Invalid input in Transient map: " + "buyerMSP");
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     System.out.printf("TransferAsset: verify asset %s exists\n", assetID);
    //     byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);

    //     if (assetJSON == null || assetJSON.length == 0) {
    //         String errorMessage = String.format("Asset %s does not exist in the collection", assetID);
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     verifyClientOrgMatchesPeerOrg(ctx);
    //     Asset thisAsset = Asset.deserialize(assetJSON);
    //     // Verify transfer details and transfer owner
    //     verifyAgreement(ctx, assetID, thisAsset.getOwner(), buyerMSP);

    //     TransferAgreement transferAgreement = ReadTransferAgreement(ctx, assetID);
    //     if (transferAgreement == null) {
    //         String errorMessage = String.format("TransferAgreement does not exist for asset: %s", assetID);
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     // Transfer asset in private data collection to new owner
    //     String newOwner = transferAgreement.getBuyerID();
    //     thisAsset.setOwner(newOwner);

    //     // Save updated Asset to collection
    //     System.out.printf("Transfer Asset: collection %s, ID %s to owner %s\n", ASSET_COLLECTION_NAME, assetID, newOwner);
    //     stub.putPrivateData(ASSET_COLLECTION_NAME, assetID, thisAsset.serialize());

    //     // Delete the key from owners collection
    //     String ownersCollectionName = getCollectionName(ctx);
    //     stub.delPrivateData(ownersCollectionName, assetID);

    //     // Delete the transfer agreement from the asset collection
    //     CompositeKey aggKey = stub.createCompositeKey(AGREEMENT_KEYPREFIX, assetID);
    //     System.out.printf("AgreeToTransfer deleteKey: collection %s, ID %s, Key %s\n", ASSET_COLLECTION_NAME, assetID, aggKey);
    //     stub.delPrivateData(ASSET_COLLECTION_NAME, aggKey.toString());
    // }

    /**
     * Deletes asset & related details from the ledger.
     * Input in transient map: asset_delete
     *
     * This deletes the private data, but does not trigger an immediate cleanup
     * of the history. To specifically force removal right now use purge
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteSbom(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("sbomDelete")) {
            String errorMessage = String.format("DeleteAsset call must specify 'sbomDelete' in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("sbomDelete");
        final String serialNumber;

        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            serialNumber = json.getString("serialNumber");

        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error: %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        System.out.printf("DeleteAsset: verify asset %s exists\n", serialNumber);
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, serialNumber);

        if (assetJSON == null || assetJSON.length == 0) {
            String errorMessage = String.format("Asset %s does not exist", serialNumber);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }
        String ownersCollectionName = getCollectionName(ctx);
        byte[] apdJSON = stub.getPrivateData(ownersCollectionName, serialNumber);

        if (apdJSON == null || apdJSON.length == 0) {
            String errorMessage = String.format("Failed to read asset from owner's Collection %s", ownersCollectionName);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }
        verifyClientOrgMatchesPeerOrg(ctx);

        // delete the key from asset collection
        System.out.printf("DeleteAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, serialNumber);
        stub.delPrivateData(ASSET_COLLECTION_NAME, serialNumber);

        // Finally, delete private details of asset
        stub.delPrivateData(ownersCollectionName, serialNumber);
    }

    /**
     * Purges the history of the asset from Private Data
     * (delete does not need to be called as well)
     * Input in transient map: asset_delete
     *
     * @param ctx the transaction context
     */
    // @Transaction(intent = Transaction.TYPE.SUBMIT)
    // public void PurgeAsset(final Context ctx) {
    //     ChaincodeStub stub = ctx.getStub();
    //     Map<String, byte[]> transientMap = ctx.getStub().getTransient();
    //     if (!transientMap.containsKey("asset_purge")) {
    //         String errorMessage = String.format("PurgeAsset call must specify 'asset_purge' in Transient map input");
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     byte[] transientAssetJSON = transientMap.get("asset_purge");
    //     final String assetID;

    //     try {
    //         JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
    //         assetID = json.getString("assetID");

    //     } catch (Exception err) {
    //         String errorMessage = String.format("TransientMap deserialized error: %s ", err);
    //         System.err.println(errorMessage);
    //         throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
    //     }

    //     // Note that there is no check here to see if the id exist; it might have been 'deleted' already
    //     // so a check here is pointless. We would need to call purge irrespective of the result
    //     // A delete can be called before purge, but is not essential

    //     String ownersCollectionName = getCollectionName(ctx);
    //     verifyClientOrgMatchesPeerOrg(ctx);

    //     // delete the key from asset collection
    //     System.out.printf("PurgeAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
    //     stub.purgePrivateData(ASSET_COLLECTION_NAME, assetID);

    //     // Finally, delete private details of asset
    //     System.out.printf("PurgeAsset: collection %s, ID %s\n", ownersCollectionName, assetID);
    //     stub.purgePrivateData(ownersCollectionName, assetID);
    // }


    // Used by TransferAsset to verify that the transfer is being initiated by the owner and that
    // the buyer has agreed to the same appraisal value as the owner
    // private void verifyAgreement(final Context ctx, final String serialNumber, final String owner, final String buyerMSP) {
    //     String clienID = ctx.getClientIdentity().getId();

    //     // Check 1: verify that the transfer is being initiatied by the owner
    //     if (!clienID.equals(owner)) {
    //         throw new ChaincodeException("Submitting client identity does not own the asset", AssetTransferErrors.INVALID_ACCESS.toString());
    //     }

    //     // Check 2: verify that the buyer has agreed to the appraised value
    //     String collectionOwner = getCollectionName(ctx); // get owner collection from caller identity
    //     String collectionBuyer = buyerMSP + "PrivateCollection";

    //     // Get hash of owners agreed to value
    //     byte[] ownerAppraisedValueHash = ctx.getStub().getPrivateDataHash(collectionOwner, serialNumber);
    //     if (ownerAppraisedValueHash == null) {
    //         throw new ChaincodeException(String.format("Hash of appraised value for %s does not exist in collection %s", serialNumber, collectionOwner));
    //     }

    //     // Get hash of buyers agreed to value
    //     byte[] buyerAppraisedValueHash = ctx.getStub().getPrivateDataHash(collectionBuyer, serialNumber);
    //     if (buyerAppraisedValueHash == null) {
    //         throw new ChaincodeException(String.format("Hash of appraised value for %s does not exist in collection %s. AgreeToTransfer must be called by the buyer first.", assetID, collectionBuyer));
    //     }

    //     // Verify that the two hashes match
    //     if (!Arrays.equals(ownerAppraisedValueHash, buyerAppraisedValueHash)) {
    //         throw new ChaincodeException(String.format("Hash for appraised value for owner %x does not match value for seller %x", ownerAppraisedValueHash, buyerAppraisedValueHash));
    //     }
    // }

    private void verifyClientOrgMatchesPeerOrg(final Context ctx) {
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        String peerMSPID = ctx.getStub().getMspId();

        if (!peerMSPID.equals(clientMSPID)) {
            String errorMessage = String.format("Client from org %s is not authorized to read or write private data from an org %s peer", clientMSPID, peerMSPID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }
    }

    private String getCollectionName(final Context ctx) {

        // Get the MSP ID of submitting client identity
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        // Create the collection name
        return clientMSPID + "PrivateCollection";
    }

}
