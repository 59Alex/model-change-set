# Historical Java 27 baseline — superseded

[← Back to documentation and versions / Назад к документации и версиям](index.md)

The Java 27-only baseline introduced in version **0.2.0** has been superseded by
version **0.3.0**. The current minimum is **Java 17**, with supported LTS runtimes
**17, 21, and 25**, plus **Java 27** as a supported **non-LTS** runtime.

Only the old Java 27 **minimum** is superseded. Running the current library on
Java 27 is supported, using the same Java 17-targeted artifact.

Use [Java support and migration](JAVA-SUPPORT.md) for the current build commands,
runtime matrix, and compatibility contract. The library no longer requires
`HashSet.newHashSet`, sequenced-collection APIs, or an instance-main launcher.

The comparison algorithm, model records, defensive copying, and input validation
remain in place. This historical note is retained so existing links do not
mistakenly imply that the current release requires Java 27.

[← Back to documentation and versions / Назад к документации и версиям](index.md)
