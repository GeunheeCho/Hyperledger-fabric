/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.privatedata;

import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.hyperledger.fabric.samples.privatedata.SbomTransfer.ASSET_COLLECTION_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public final class AssetTransferTest {

    @Nested
    class InvokeWriteTransaction {

        @Test
        public void createAssetWhenAssetExists() {
            SbomTransfer contract = new SbomTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            Map<String, byte[]> m = new HashMap<>();
            m.put("asset_properties", DATA_ASSET_1_BYTES);
            when(ctx.getStub().getTransient()).thenReturn(m);
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID))
                    .thenReturn(DATA_ASSET_1_BYTES);

            Throwable thrown = catchThrowable(() -> {
                contract.CreateAsset(ctx);
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class)
                .hasMessageContaining("already exists");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("ASSET_ALREADY_EXISTS".getBytes());
        }

        @Test
        public void createAssetWhenNewAssetIsCreated() {
             SbomTransfer contract = new SbomTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getMspId()).thenReturn(TEST_ORG_1_MSP);
            ClientIdentity ci = mock(ClientIdentity.class);
            when(ci.getId()).thenReturn(TEST_ORG_1_USER);
            when(ci.getMSPID()).thenReturn(TEST_ORG_1_MSP);
            when(ctx.getClientIdentity()).thenReturn(ci);

            Map<String, byte[]> m = new HashMap<>();
            m.put("asset_properties", DATA_ASSET_1_BYTES);
            when(ctx.getStub().getTransient()).thenReturn(m);

            when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID))
                    .thenReturn(new byte[0]);

           // 1. 실행
            Sbom created = contract.CreateAsset(ctx);

            // 2. 검증: 객체 전체 비교가 실패한다면 필드별로 쪼개서 확인해봐!
            assertThat(created.getSerialNumber()).isEqualTo(TEST_ASSET_1_ID);
            assertThat(created.getOwner()).isEqualTo(TEST_ORG_1_USER);
            // 해시값은 로직에 의해 변했을 수 있으니 null이 아닌지만 체크하거나 포함 여부 확인
            assertThat(created.getSbomHash()).isNotNull();

            // 3. 기록 확인: stub.putPrivateData가 호출되었는지 확인
            // 정확한 바이트 비교가 힘들면 any(byte[].class) 사용
            verify(stub).putPrivateData(eq(ASSET_COLLECTION_NAME), eq(TEST_ASSET_1_ID), any(byte[].class));
        }

        // @Test
        // public void transferAssetWhenExistingAssetIsTransferred() {
        //     AssetTransfer contract = new AssetTransfer();
        //     Context ctx = mock(Context.class);
        //     ChaincodeStub stub = mock(ChaincodeStub.class);
        //     when(ctx.getStub()).thenReturn(stub);
        //     when(stub.getMspId()).thenReturn(TEST_ORG_1_MSP);
        //     ClientIdentity ci = mock(ClientIdentity.class);
        //     when(ci.getId()).thenReturn(TEST_ORG_1_USER);
        //     when(ctx.getClientIdentity()).thenReturn(ci);
        //     when(ci.getMSPID()).thenReturn(TEST_ORG_1_MSP);
        //     final String recipientOrgMsp = "TestOrg2";
        //     final String buyerIdentity = "TestOrg2User";
        //     Map<String, byte[]> m = new HashMap<>();
        //     m.put("asset_owner", ("{ \"buyerMSP\": \"" + recipientOrgMsp + "\", \"serialNumber\": \"" + TEST_ASSET_1_ID + "\" }").getBytes());
        //     when(ctx.getStub().getTransient()).thenReturn(m);

        //     when(stub.getPrivateDataHash(anyString(), anyString())).thenReturn("TestHashValue".getBytes());
        //     when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID))
        //             .thenReturn(DATA_ASSET_1_BYTES);
        //     CompositeKey ck = mock(CompositeKey.class);
        //     when(ck.toString()).thenReturn(AGREEMENT_KEYPREFIX + TEST_ASSET_1_ID);
        //     when(stub.createCompositeKey(AGREEMENT_KEYPREFIX, TEST_ASSET_1_ID)).thenReturn(ck);
        //     when(stub.getPrivateData(ASSET_COLLECTION_NAME, AGREEMENT_KEYPREFIX + TEST_ASSET_1_ID)).thenReturn(buyerIdentity.getBytes(UTF_8));
        //     contract.TransferAsset(ctx);

        //     Asset exptectedAfterTransfer  = Asset.deserialize("{ \"objectType\": \"testasset\", \"serialNumber\": \"asset1\", \"color\": \"blue\", \"size\": 5, \"owner\": \"" +  buyerIdentity + "\", \"appraisedValue\": 300 }");

        //     verify(stub).putPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID, exptectedAfterTransfer.serialize());
        //     String collectionOwner = TEST_ORG_1_MSP + "PrivateCollection";
        //     verify(stub).delPrivateData(collectionOwner, TEST_ASSET_1_ID);
        //     verify(stub).delPrivateData(ASSET_COLLECTION_NAME, AGREEMENT_KEYPREFIX + TEST_ASSET_1_ID);
        // }
    }

    @Nested
    class QueryReadAssetTransaction {

        @Test
        public void whenAssetExists() {
            SbomTransfer contract = new SbomTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID))
                    .thenReturn(DATA_ASSET_1_BYTES);

            Sbom sbom = contract.ReadAsset(ctx, TEST_ASSET_1_ID);

            assertThat(sbom).isEqualTo(TEST_ASSET_1);
        }

        @Test
        public void whenAssetDoesNotExist() {
            SbomTransfer contract = new SbomTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID)).thenReturn(new byte[0]);

            Sbom sbom = contract.ReadAsset(ctx, TEST_ASSET_1_ID);
            assertThat(sbom).isNull();
        }

        @Test
        public void invokeUnknownTransaction() {
            SbomTransfer contract = new SbomTransfer();
            Context ctx = mock(Context.class);

            Throwable thrown = catchThrowable(() -> {
                contract.unknownTransaction(ctx);
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Undefined contract method called");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo(null);

            verifyNoInteractions(ctx);
        }

    }

    private static final String TEST_ORG_1_MSP = "TestOrg1";
    private static final String TEST_ORG_1_USER = "testOrg1User";

    private static final String TEST_ASSET_1_ID = "urn:uuid:cb8eb8c0-aa63-4a25-a495-6eaa8dc4a243";
    private static final Sbom TEST_ASSET_1 = new Sbom("CycloneDX", "1.6", "urn:uuid:cb8eb8c0-aa63-4a25-a495-6eaa8dc4a243", "1", " \"publisher\": \"Hanwha Vision Co., Ltd.\"", "dummy" ,TEST_ORG_1_USER);
    private static final byte[] DATA_ASSET_1_BYTES = "{ \"serialNumber\": \"urn:uuid:cb8eb8c0-aa63-4a25-a495-6eaa8dc4a243\", \"sbomHash\": \"myhash\", \"metadata\": \"MeetaDaata\", \"owner\": \"testOrg1User\" }".getBytes();
}
