# EDC extensions for instrumentation with Micrometer

EDC provides extensions for instrumentation with the [Micrometer](https://micrometer.io/) metrics library to automatically collect metrics from the host system, JVM, and frameworks and libraries used in EDC (including OkHttp, Jetty, Jersey and ExecutorService).

See [Micrometer Extension](https://github.com/eclipse-edc/Connector/tree/main/extensions/common/metrics/micrometer-core).

## Custometer Micrometer Extension

This extension is a custom extension for Micrometer. It configures the `MeterRegistry` to use specific service level objectives (SLOs) for distribution statistics.

### Bucket Boundaries
To modify the bucket metrics boundaries you just need to set the content of the field double[] SERVICE_LEVEL_OBJECTIVES

The current bucket boundaries are:
    - 5 milliseconds
    - 25 milliseconds
    - 50 milliseconds
    - 1 second

That will allow us to categorize and aggregating observed values for histograms and statistic oriented requests.