# Omejdn Configuration for the DAPS use case

This repository contains the necessary configuration templates to use an Omejdn instance as a DAPS as described in [IDS-G](https://github.com/International-Data-Spaces-Association/IDS-G).
This document lists the necessary steps to adapt them to your use case.

## Important Considerations

A Dynamic Attribute Provisioning System (DAPS) has the intent to assertain certain attributes to organizations and connectors.
Hence, third parties do not need to trust the latter **provided they trust the DAPS assertions**.
This is usually a matter of configuration on the verifying party's end which is not part of this document.
In general, it requires registering both the DAPS certificate and its name as a trusted identity.

**This document builds a DAPS for testing purposes only**

## Requirements

- [Omejdn Server](https://github.com/Fraunhofer-AISEC/omejdn-server)'s dependencies
- [OpenSSL](https://www.openssl.org/)

This repository has submodules.
Make sure to download them using `git submodule update --init --remote`

## Minimal Configuration

The configuration consists of the following steps:

1. Downloading Omejdn
1. Generating a DAPS secret key and certificate
1. Provisioning the provided config files and registering connectors
1. Starting the server

All commands are to be run from the repository's root directory

### Downloading Omejdn

Omejdn is included as a submodule in this repository.
To retrieve it, run:

```
git submodule update --init --remote
```

### DAPS Key Generation

First, you need to generate a signing key for Omejdn.
This can be done using openssl and the following command.
It is recommended to fill out the form, but not strictly necessary for test setups.

```
$ openssl req -newkey rsa:2048 -new -nodes -x509 -days 3650 -keyout daps.key -out daps.cert
```

This will create two files:

* `daps.key` is the private signing key. It should **never** be known to anyone but the server, since anyone with this file can issue arbitrary DATs.
* `daps.cert` is the certificate file. It is not confidential and is necessary to validate DATs. It must be made available to any DAT verifying entity.

### Config Files

#### Server config

Open the provided `config/omejdn.yml` and replace every occurence of `http://daps.example.com` with your server's URI.

#### Registering Connectors

Connectors can be registered at any point by adding clients to the `config/clients.yml` file and placing the certificate in the right place.
To ease this process, use the provided script `scripts/register_connector.sh`

Usage:

```
$ scripts/register_connector.sh NAME SECURITY_PROFILE CERTIFICATE_FILE >> config/clients.yml
```

The `SECURITY_PROFILE` and `CERTIFICATE` arguments are optional. Values for the former include:

- idsc:BASE_SECURITY_PROFILE (default)
- idsc:TRUST_SECURITY_PROFILE
- idsc:TRUST_PLUS_SECURITY_PROFILE

The script will automatically generate new client certificates (`keys/NAME.cert`) and keys (`keys/NAME.key`) if you do not provide a certificate manually.


### Starting Omejdn

Replace the `keys` and `config` folder inside `omejdn-server` by the ones in this folder.
Copy `daps.key` into `omejdn-server`.

Navigate into `omejdn-server`
Now you may start Omejdn by executing

```
$ bundle install
$ ruby omejdn.rb
```

The endpoint for issuing DATs is `/token`. You may use it as described in [IDS-G](https://github.com/International-Data-Spaces-Association/IDS-G).

A script to quickly test your setup can be found in `scripts` (requires jq to be installed to format JSON).
Be aware that Omejdn has its own folder labeled scripts, which is not the one mentioned here.

```
$ scripts/test.sh CLIENT_NAME
```

## Testing the DAPS

 You can test the DAPS implementation with the provided Dockerfile, however, previous configuration is required. Before creating the image with the Dockerfile, the certificates and keys for 2 clients, and the DAPS signing key should be placed in the `keys` directory. A configuration file should be placed in `tests/test_config.txt`. The configuration file contains information about the clients in order to correctly request DAT tokens. An example configuration file is as follows:
 ```
 iss=01:02:21:92:F6:27:E2:05:B6:6C:A7:D0:6B:33:63:4C:CC:FB:1C:30:keyid:01:02:21:92:F6:27:E2:05:B6:6C:A7:D0:6B:33:63:4C:CC:FB:1C:30
 aud=idsc:IDS_CONNECTORS_ALL
 iss_daps=http://daps.example.com
 securityProfile=idsc:BASE_SECURITY_PROFILE
 referringConnector=http://test1.demo
 @type=ids:DatPayload
 @context=https://w3id.org/idsa/contexts/context.jsonld
 scope=idsc:IDS_CONNECTOR_ATTRIBUTES_ALL
 transportCertsSha256=39d625f6069d1ad5947160ebc7686e4ac6dfe52876d82ba1ef18ed9640bd0db7
 keyPath=../keys/test1.key
 keyPath2=../keys/test2.key
 iss2=70:43:A0:57:58:49:1D:1A:5F:61:8C:35:4D:74:76:C7:FF:A4:44:97:keyid:70:43:A0:57:58:49:1D:1A:5F:61:8C:35:4D:74:76:C7:FF:A4:44:97
 url=http://localhost:4567/
 ```
 Each line in the configuration file is an attribute required in that specific order and to be separated with an equal sign without spaces. The attributes refer to:
 - iss: `client_id` for the first client.
 - aud: Audience for the first client.
 - iss_daps: DAPS issuer for DAT tokens.
 - securityProfile: Expected security profile in DAT.
 - referringConnector: URI of the first client.
 - @type: Type of the DAT token.
 - @context: Context containing the IDS classes.
 - scope: List of scopes in the DAT.
 - transporteCertSha256: The public transportation key from the first client used to request a DAT token.
 - keyPath: Path to the first client's key.
 - keyPath2: Path to the second client's key.
 - iss2: `client_id` for the second client.
 - url: Address at which the DAPS server can be contacted.

 Once all the required material for the testing is ready, we can start testing a DAPS instance by creating a docker image and the running a container. In order to create the image execute:
 ```
 $ docker build . -t daps-test
 ```
And then to run the container with the tests simply execute:
 ```
 $ docker run --name=test daps-test
 ```

## Going Forward

While the above configuration should be sufficient for testing purposes,
you probably want to consider the following ideas in the long term:

#### Transport Encryption

Run Omejdn behind a Proxy with https support, such as [Nginx](https://nginx.org/en/).
Do not forget to edit `config/omejdn.yml` to reflect the new address.

#### Certificate Authorities

As described in this document, all certificates are self-signed.
Depending on your use-case, you may want to use certificates issued by trusted Certificate Authorities for both the DAPS and the Connectors.

#### Omejdn Config API

If you do not have Access to the DAPS or want to edit connectors (=clients) and configuration remotely,
you may enable Omejdn's Config API.

To use it, uncomment the relevant lines (remove the # symbol) in `config/scope_mapping.yml`,
then edit or register a client with an attribute like this:

```
- key: omejdn
- value: admin
```

Add the scope `omejdn:admin` to its list of allowed scopes.

This client may now use the Omejdn Config API as documented [here](https://github.com/Fraunhofer-AISEC/omejdn-server/blob/master/API.md).
