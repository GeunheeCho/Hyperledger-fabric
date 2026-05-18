'use strict';

const { WorkloadModuleBase } = require('@hyperledger/caliper-core');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path'); 

const sbomRawData = fs.readFileSync(path.resolve(__dirname, './SBOM_TEST.json'), 'utf8');
const sbomJson = JSON.parse(sbomRawData);
function encrypt(text) {
    return crypto.createHash('sha256').update(text).digest('hex');
}

class MyWorkload extends WorkloadModuleBase {
    constructor() {
        super();
        this.txIndex = 0;
    }

    async submitTransaction() {
        const serialNumber = sbomJson.serialNumber;
	const timeStamp = Date.now();
        sbomJson.serialNumber = `${serialNumber}_${this.workerIndex}_${timeStamp}`;

        const sbomHash = encrypt(JSON.stringify(sbomJson));

        const sbomProperties = {
            ...sbomJson,
            sbomHash: sbomHash,
        };
        const jsonString = JSON.stringify(sbomProperties);
        const base64EncodedData = Buffer.from(jsonString).toString('base64');

        const request = {
            contractId: 'basic',
            contractFunction: 'CreateAsset',
            invokerIdentity: 'User1',
            contractArguments: [base64EncodedData],
            readOnly: false,
        };

        await this.sutAdapter.sendRequests(request);
    }
}

function createWorkloadModule() {
    return new MyWorkload();
}

module.exports.createWorkloadModule = createWorkloadModule;
