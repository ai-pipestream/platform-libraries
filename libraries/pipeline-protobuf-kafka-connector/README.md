# Pipeline Protobuf Kafka Connector

Custom SmallRye Reactive Messaging connector for Protobuf + Apicurio v3.

## Architecture

This is a Quarkus extension with two modules:

- **runtime**: The actual connector implementation (`ProtobufKafkaConnector`)
- **deployment**: Build-time processing for auto-configuration

## Features

- ✅ Apicurio v3 Protobuf serialization/deserialization
- ✅ UUID key handling
- ✅ Zero-config operation for Protobuf types
- ✅ Full control over Kafka configuration
- ✅ No conflicts with Quarkus's Kafka extension

## Status

🚧 **In Development** - Basic structure created, implementation in progress.

