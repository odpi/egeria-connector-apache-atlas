<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the ODPi Egeria project. -->

# Conformance Test Suite

This directory contains information regarding the Conformance Test Suite (CTS) and the Apache Atlas connector.

## Charts

The `charts` sub-directory contains a Helm chart to automate the execution of the CTS against the Apache Atlas
connector, to produce a set of repeatable CTS results.

## Results

The `results` sub-directory contains results of running the CTS against the Apache Atlas connector. For each release,
you will find the following details:

- `openmetadata.conformance.testlab.results` - the detailed results, as produced by the CTS workbench itself
- Description of the k8s environment
    - `<version>.deployment` - details of the deployed components used for the test
    - `<version>.configmap` - details of the variables used within the components of the test
- The OMAG server configurations:
    - `omag.server.atlas.config` - the configuration of the Apache Atlas connector (proxy)
    - `omag.server.cts.config` - the configuration of the CTS workbench
- The cohort registrations:
    - `cohort.coco.atlas.local` - the local Apache Atlas connector (proxy) cohort registration information
    - `cohort.coco.atlas.remote` - the cohort members considered remote from the Apache Atlas connector (proxy)'s perspective
    - `cohort.coco.cts.local` - the local CTS Workbench cohort registration
    - `cohort.coco.cts.remote` - the cohort members considered remote from the CTS Workbench's perspective

## Egeria 2.10

| Apache Atlas version | Conformant profile(s) | Notes |
| :--- | :--- | :--- |
| [v2.0.0](results/2.10/2.0.0) | None | (see known issues and [known issue](https://github.com/odpi/egeria-connector-hadoop-ecosystem/issues/228)) |

## Egeria 2.5

| Apache Atlas version | Conformant profile(s) | Notes |
| :--- | :--- | :--- |
| [v2.0.0](results/2.5/2.0.0) | None | (see known issues and [known issue](https://github.com/odpi/egeria-connector-hadoop-ecosystem/issues/228)) |

## Egeria 2.2

| Apache Atlas version | Conformant profile(s) | Notes |
| :--- | :--- | :--- |
| [v2.0.0](results/2.2/2.0.0) | None | (see known issues) |

## Egeria 2.1

| Apache Atlas version | Conformant profile(s) | Notes |
| :--- | :--- | :--- |
| [v2.0.0](results/2.1/2.0.0) | None | (see known issues) |

## Egeria 1.6

| Apache Atlas version | Conformant profile(s) | Notes |
| :--- | :--- | :--- |
| [v2.0.0](results/1.6/2.0.0) | None | (see known issues) |

## Egeria 1.5

| Apache Atlas version | Conformant profile(s) | Notes |
| :--- | :--- | :--- |
| [v2.0.0](results/1.5/2.0.0) | None | (see known issues) |

## Egeria 1.3

| Apache Atlas version | Conformant profile(s) | Notes |
| :--- | :--- | :--- |
| [v2.0.0](results/1.3/2.0.0) | None | (see known issues) |

## Known issues

The following issues are known that prevent the Apache Atlas connector from conforming to the mandatory profile of the
Conformance Test Suite (CTS):

- It is currently not possible to search for the empty string (tracked under issue [#66](https://github.com/odpi/egeria-connector-apache-atlas/issues/66) / [JIRA-ATLAS-3573](https://issues.apache.org/jira/browse/ATLAS-3573))
- Certain strings are problematic for search, particularly contains searches (tracked under issue [#67](https://github.com/odpi/egeria-connector-apache-atlas/issues/67) / [JIRA-ATLAS-3574](https://issues.apache.org/jira/browse/ATLAS-3574))

----
License: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
Copyright Contributors to the ODPi Egeria project.
