'use strict';

const { WorkloadModuleBase } = require('@hyperledger/caliper-core');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const sbomRawData = fs.readFileSync(path.resolve(__dirname, './SBOM_TEST.json'), 'utf8');
const sbomJson = JSON.parse(sbomRawData);
const baseSerialNumber = sbomJson.serialNumber;

function encrypt(text) {
    return crypto.createHash('sha256').update(text).digest('hex');
}

class MyWorkload extends WorkloadModuleBase {
    constructor() {
        super();
        this.txIndex = 0;
    }

    async submitTransaction() {
        this.txIndex++;
        
        const timestamp = Date.now();
        const txData = { ...sbomJson };
        txData.serialNumber = `${baseSerialNumber}_${this.txIndex}_${timestamp}`;

        const sbomHash = encrypt(JSON.stringify(txData));

        const sbomProperties = {
            ...txData,
            sbomHash: sbomHash,
        };
        const jsonString = JSON.stringify(sbomProperties);
        const sbomBuffer = Buffer.from(jsonString, 'utf8');

        const request = {
            contractId: 'sbom',
            contractFunction: 'CreateAsset',
            invokerIdentity: 'User1',
            contractArguments: [],
            transientMap:{
                'sbom_property': sbomBuffer
            },
            readOnly: false,
        };

        await this.sutAdapter.sendRequests(request);
    }
}

function createWorkloadModule() {
    return new MyWorkload();
}

module.exports.createWorkloadModule = createWorkloadModule;
