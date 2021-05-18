<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

[![License](https://img.shields.io/github/license/odpi/egeria-connector-hadoop-ecosystem)](LICENSE)
[![Build](https://github.com/odpi/egeria-connector-hadoop-ecosystem/workflows/Build/badge.svg)](https://github.com/odpi/egeria-connector-hadoop-ecosystem/actions/workflows/merge.yml?query=workflow%3ABuild)
[![CodeQL](https://github.com/odpi/egeria-connector-hadoop-ecosystem/workflows/CodeQL/badge.svg)](https://github.com/odpi/egeria-connector-hadoop-ecosystem/actions/workflows/codeql-analysis.yml)
[![Release](https://img.shields.io/maven-central/v/org.odpi.egeria/egeria-connector-hadoop-ecosystem-package?label=release)](http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.odpi.egeria&a=egeria-connector-hadoop-ecosystem-package&v=RELEASE&c=jar-with-dependencies)
[![Development](https://img.shields.io/nexus/s/org.odpi.egeria/egeria-connector-hadoop-ecosystem-package?label=development&server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/org/odpi/egeria/egeria-connector-hadoop-ecosystem-package/)

# Hadoop Ecosystem Repository Connectors

This repository houses the ODPi Egeria connectors for various Hadoop ecosystem components:

- [Apache Atlas connector](apache-atlas-adapter) implements read-only connectivity to the Apache Atlas metadata repository.
- [Apache Ranger](https://ranger.apache.org) is a framework to enable, monitor and manage comprehensive data security
    across the Hadoop platform.  (Coming soon.)

## Getting started

### TL;DR

The quick version:

1. Download the latest connectors from: https://odpi.jfrog.io/odpi/egeria-snapshot-local/org/odpi/egeria/egeria-connector-hadoop-ecosystem-package/2.7-SNAPSHOT/egeria-connector-hadoop-ecosystem-package-2.7-SNAPSHOT-jar-with-dependencies.jar
1. Download the latest Egeria core from: https://odpi.jfrog.io/odpi/egeria-snapshot-local/org/odpi/egeria/server-chassis-spring/2.7-SNAPSHOT/server-chassis-spring-2.7-SNAPSHOT.jar
1. Rename the downloaded Egeria core file to `egeria-server-chassis-spring.jar`.
1. Download the `truststore.p12` file from: https://github.com/odpi/egeria/blob/master/truststore.p12
1. Run the following command to start Egeria from the command-line, waiting for the final line of output indicating the
    server is running and ready for configuration:
    ```bash
    $ export STRICT_SSL=false
    $ java -Dloader.path=. -jar egeria-server-chassis-spring.jar
     ODPi Egeria
        ____   __  ___ ___    ______   _____                                 ____   _         _     ___
       / __ \ /  |/  //   |  / ____/  / ___/ ___   ____ _   __ ___   ____   / _  \ / / __    / /  / _ /__   ____ _  _
      / / / // /|_/ // /| | / / __    \__ \ / _ \ / __/| | / // _ \ / __/  / /_/ // //   |  / _\ / /_ /  | /  _// || |
     / /_/ // /  / // ___ |/ /_/ /   ___/ //  __// /   | |/ //  __// /    /  __ // // /  \ / /_ /  _// / // /  / / / /
     \____//_/  /_//_/  |_|\____/   /____/ \___//_/    |___/ \___//_/    /_/    /_/ \__/\//___//_/   \__//_/  /_/ /_/

     :: Powered by Spring Boot (v2.2.2.RELEASE) ::


    No OMAG servers listed in startup configuration
    Thu Jan 02 11:30:10 GMT 2020 OMAG server platform ready for more configuration
    ```
1. Follow the detailed instructions for configuring the connector(s) you want to use, either [Apache Atlas](apache-atlas-adapter)
   or Apache Ranger (coming soon).

### Obtain the connector

You can either download the latest released or snapshot version of the connector directly from ODPi, or build the
connector yourself. In both cases, once you have the jar file for the connector
(`egeria-connector-hadoop-ecosystem-package-VERSION-jar-with-dependencies.jar`) this needs to be copied to a
location where it can be run alongside the OMAG Server Platform from Egeria core itself. For example, this could be
placing the file into the `/lib` directory as `/lib/egeria-connector-hadoop-ecosystem-package-VERSION-jar-with-dependencies.jar`.

#### Download from ODPi

To download a pre-built version of the connector, use either of the following URLs (depending on whether you want an
officially-released version or the latest snapshot):

- Release: https://odpi.jfrog.io/odpi/egeria-release-local/org/odpi/egeria/egeria-connector-hadoop-ecosystem-package/2.5/egeria-connector-hadoop-ecosystem-package-2.5-jar-with-dependencies.jar
- Snapshot: https://odpi.jfrog.io/odpi/egeria-snapshot-local/org/odpi/egeria/egeria-connector-hadoop-ecosystem-package/2.7-SNAPSHOT/egeria-connector-hadoop-ecosystem-package-2.7-SNAPSHOT-jar-with-dependencies.jar

#### Building the connector yourself

Alternatively, you can build the connector yourself. To do this, you'll need to first clone this repository and then
build through Maven using `mvn clean install`. After building, the connector is available as:

```text
distribution/target/egeria-connector-hadoop-ecosystem-package-VERSION-jar-with-dependencies.jar
```

### Configure security

There are [multiple options to configure the security of your environment](docs/security/README.md) for this connector,
but this must be done prior to starting up the connector itself (step below).

For simple tests, if you can run your Apache Atlas environment with only its most basic security and without HTTPS, then
there is nothing additional you need to configure for the connector.

Alternatively, if you can still use basic authentication (username and password) but must run Apache Atlas via HTTPS,
and you simply want to test things out, the simplest (but most insecure) option is to set the environment variable
`STRICT_SSL` to `false` using something like the following prior to starting up the OMAG Server Platform:

```bash
export STRICT_SSL=false
```

Note that this will disable all certificate validation for SSL connections made between Egeria and your Apache Atlas
environment, so is inherently insecure.

Note that in any case, having a `truststore.p12` file available to the server chassis is required -- the simplest is to
ensure that Egeria's own (https://github.com/odpi/egeria/blob/master/truststore.p12) is placed in the directory in which
you are running the server chassis.

### Startup the OMAG Server Platform

You can startup the OMAG Server Platform with this connector ready-to-be-configured by running:

```bash
$ java -Dloader.path=/lib -jar server-chassis-spring-VERSION.jar
```

(This command will startup the OMAG Server Platform, including all libraries
in the `/lib` directory as part of the classpath of the OMAG Server Platform.)

### Configure the connector(s) you want to use

See the detailed instructions for configuring the connector(s) you want to use:

- [Apache Atlas](apache-atlas-adapter)
- Apache Ranger (coming soon)

----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.

