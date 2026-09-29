plugins {
    java
}

group = "br.com.paragonn"
version = "1.0.0"

java {
    // Compila com um JDK moderno, mas gera bytecode Java 8 (servidores 1.8.8).
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.roinujnosde.me/releases/")
    maven("https://maven.citizensnpcs.co/repo")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    // Sem transitivas: o bungeecord-chat 1.8-SNAPSHOT não está mais publicado (e não é usado aqui).
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") { isTransitive = false }
    compileOnly("net.sacredlabyrinth.phaed.simpleclans:SimpleClans:2.18.1") { isTransitive = false }
    compileOnly("net.citizensnpcs:citizensapi:2.0.30-SNAPSHOT") { isTransitive = false }
    compileOnly("me.clip:placeholderapi:2.11.6") { isTransitive = false }
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveBaseName.set("ParagonnDominacao")
}
