plugins {
    `java-library`
}

group = "dev.amll.saltplayer"
version = "1.0.0"

layout.buildDirectory.set(file("out-plugin"))

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    named("main") {
        java.srcDir("src/spwApiStubs/java")
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.isIncremental = false
    options.encoding = "UTF-8"
}

val pluginClass = "dev.amll.saltplayer.ttml.AmllTtmlPlugin"
val pluginId = "dev.amll.saltplayer.ttml"
val pluginName = "AMLL TTML Loader"
val pluginDescription = "Searches AMLL TTML DB, converts TTML word lyrics to Salt Player SPL, and falls back to local lyrics."
val pluginVersion = project.version.toString()
val pluginProvider = "GaBoron"
val pluginRepository = "https://github.com/amll-dev/amll-ttml-db"

tasks.named<Jar>("jar") {
    includeEmptyDirs = false
    exclude("com/xuncorp/**", "org/pf4j/**", "**/ConverterSmoke.class")
    manifest {
        attributes(
            "Plugin-Class" to pluginClass,
            "Plugin-Id" to pluginId,
            "Plugin-Name" to pluginName,
            "Plugin-Description" to pluginDescription,
            "Plugin-Version" to pluginVersion,
            "Plugin-Provider" to pluginProvider,
            "Plugin-Has-Config" to "true",
            "Plugin-Open-Source-Url" to pluginRepository,
        )
    }
}

tasks.register<Jar>("plugin") {
    archiveFileName.set("AMLL-TTML-Loader-$pluginVersion.zip")
    destinationDirectory.set(layout.buildDirectory.dir("plugin"))
    archiveExtension.set("zip")

    into("classes") {
        with(tasks.named<Jar>("jar").get())
    }

    into("lib") {
        from(emptyList<File>())
    }
}
