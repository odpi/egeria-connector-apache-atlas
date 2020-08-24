<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

[![GitHub](https://img.shields.io/github/license/odpi/egeria-connector-hadoop-ecosystem)](LICENSE) [![Azure](https://dev.azure.com/odpi/egeria/_apis/build/status/odpi.egeria-connector-hadoop-ecosystem)](https://dev.azure.com/odpi/Egeria/_build) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=egeria-connector-hadoop-ecosystem&metric=alert_status)](https://sonarcloud.io/dashboard?id=egeria-connector-hadoop-ecosystem) [![Maven Central](https://img.shields.io/maven-central/v/org.odpi.egeria/egeria-connector-hadoop-ecosystem)](https://mvnrepository.com/artifact/org.odpi.egeria/egeria-connector-hadoop-ecosystem)

![Egeria Logo](assets/img/ODPi_Egeria_Logo_color.png)

# Egeria - Open Metadata and Governance

Egeria provides the Apache 2.0 licensed open metadata and governance type system, frameworks, APIs, event payloads and interchange protocols to enable tools, engines and platforms to exchange metadata in order to get the best value from data whilst ensuring it is properly governed.

# Hadoop Ecosystem Repository Connectors

This repository houses the ODPi Egeria connectors for various Hadoop ecosystem components:

- [Apache Atlas](https://atlas.apache.org) is an open source metadata repository. This connector provides an example
    implementation for interacting with a metadata repository through the open metadata standards of Egeria.

    Note that currently the implemented connector is read-only: it only implements those methods necessary to search, retrieve,
    and communicate metadata from Atlas out into the cohort -- it does *not* currently implement the ability to update Atlas
    based on events received from other members of the cohort. (This is due to some current limitations in Apache Atlas --
    see [open issues](https://github.com/odpi/egeria-connector-apache-atlas/issues?q=is%3Aissue+is%3Aopen+label%3Aexternal).)

    Furthermore, [only a subset of the overall Open Metadata Types are currently implemented](docs/mappings/README.md).

- [Apache Ranger](https://ranger.apache.org) is a framework to enable, monitor and manage comprehensive data security
    across the Hadoop platform.  (Coming soon.)

## How it works

The Apache Atlas Repository Connector works through a combination of the following:

- Apache Atlas's REST API, itself abstracted through the Apache Atlas Java Client
- Apache Atlas's embedded Apache Kafka event bus
    - specifically the `ATLAS_ENTITIES` topic




----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.
