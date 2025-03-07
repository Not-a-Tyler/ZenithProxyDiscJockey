plugins {
    java
    id("zenithproxy-dev") version "1.0.0-SNAPSHOT"
}

group = properties["maven_group"] as String
version = properties["plugin_version"] as String

java { toolchain { languageVersion = JavaLanguageVersion.of(23) } }

zenithProxy {
    mc = properties["mc"] as String
}
