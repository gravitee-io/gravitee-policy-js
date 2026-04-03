
<!-- GENERATED CODE - DO NOT ALTER THIS OR THE FOLLOWING LINES -->
# JavaScript

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-policy-js/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-js/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-js/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-js.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-js)

## Overview
This policy lets you execute custom JavaScript scripts at any stage of request or response processing through the Gravitee gateway. It is powered by GraalJS, providing full ES6+ support, secure sandboxed execution, and modern JavaScript engine capabilities.

It replaces the legacy Nashorn-based `gravitee-policy-javascript` policy, which has been deprecated due to Nashorn's removal from recent JDKs. This is a new policy built from scratch — not a migration — and existing scripts may require adjustments.




## Errors
These templates are defined at the API level, in the "Entrypoint" section for v4 APIs, or in "Response Templates" for v2 APIs.
The error keys sent by this policy are as follows:

| Key| Parameters |
| --- | ---  |
| JS_EXECUTION_FAILURE| Interrupted with a 500 status. Occurs when the JavaScript script throws an error or exceeds the execution timeout. |



## Phases
The `js` policy can be applied to the following API types and flow phases.

### Compatible API types

* `PROXY`
* `MESSAGE`

### Supported flow phases:

* Request
* Response
* Publish
* Subscribe

## Compatibility matrix
Strikethrough text indicates that a version is deprecated.

| Plugin version| APIM| Java version |
| --- | --- | ---  |
|1.0.0 and after|4.10.x and after|21 |


## Configuration options


#### 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Override content<br>`overrideContent`| boolean|  | | Enable to override the content with the value returned by your script.|
| Read content<br>`readContent`| boolean|  | | Enable to read the content of the request or response in your script.|
| Script<br>`script`| string| ✅| | JavaScript script to evaluate.|




## Examples



## Changelog

### Changelog

