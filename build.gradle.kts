import java.time.OffsetDateTime
import java.time.ZoneOffset

plugins {
    java
    id("net.raphimc.class-token-replacer") version("1.1.4")
    idea
}

group = properties["maven_group"] as String
version = properties["plugin_version"] as String
val mc: String by properties

val javaVersion = JavaLanguageVersion.of(23) // the java version used for development
val javaReleaseVersion = JavaLanguageVersion.of(21) // the java version of the compiled plugin jar
java { toolchain { languageVersion = javaVersion } }

repositories {
    mavenLocal()
    maven("https://maven.2b2t.vc/releases")
    maven("https://libraries.minecraft.net") {
        content { includeGroup("com.mojang") }
    }
    maven("https://repo.opencollab.dev/maven-releases/") {
        content { includeGroupByRegex("org.cloudburstmc.*") }
    }
    maven("https://repo.papermc.io/repository/maven-public/") {
        content { includeGroup("com.velocitypowered") }
    }
    maven("https://repo.viaversion.com") {
        content {
            includeGroup("com.viaversion")
            includeGroup("net.raphimc")
        }
    }
    maven("https://maven.lenni0451.net/releases") {
        content {
            includeGroup("net.raphimc")
            includeGroup("net.lenni0451")
        }
    }
    mavenCentral()
}

dependencies {
    implementation(annotationProcessor("com.zenith:ZenithProxy:${mc}-SNAPSHOT")!!)
}

val runDirectory = layout.projectDirectory.dir("run")

tasks {
    withType(JavaCompile::class.java) {
        options.encoding = "UTF-8"
        options.isDeprecation = true
        options.release = javaReleaseVersion.asInt()
    }

    val copyPluginTask = register("copyPlugin", Copy::class.java) {
        group = "build"
        description = "Copy Plugin To ZenithProxy"
        from(layout.buildDirectory.dir("libs")) {
            include("*.jar")
            rename("(.*)", "plugin.jar")
        }
        into(runDirectory.dir("plugins"))
        dependsOn(build)
    }

    register("run", JavaExec::class.java) {
        group = "build"
        description = "Execute ZenithProxy With Plugin"
        classpath = sourceSets.main.get().runtimeClasspath
            // filter out duplicate classpath entries
            // we want zenith to load our plugin classes from the run directory like it would in prod
            .filter { !it.path.contains(layout.buildDirectory.asFile.get().path) }
        workingDir = runDirectory.asFile
        mainClass.set("com.zenith.Proxy")
        jvmArgs = listOf("-Xmx300m", "-XX:+UseG1GC")
        standardInput = System.`in`
        environment("ZENITH_DEV", "true")
        dependsOn(copyPluginTask)
    }

    jar {
        manifest { // metadata about the plugin build
            attributes(mapOf(
                "Date" to OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "Java-Version" to javaReleaseVersion,
                "MC-Version" to mc
            ))
        }
    }
}

sourceSets {
    main {
        classTokenReplacer {
            property("\${version}", project.version)
        }
    }
}

idea {
    module {
        excludeDirs.add(runDirectory.asFile)
        excludeDirs.add(layout.projectDirectory.dir(".idea").asFile)
    }
}
