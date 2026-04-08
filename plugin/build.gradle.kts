plugins {
    id("dev.arbjerg.lavalink.gradle-plugin") version "1.0.15"
}

base {
    archivesName = "amazonmusic-plugin"
}

lavalinkPlugin {
    name = "amazonmusic-plugin"
    apiVersion = "4.0.0"
    serverVersion = "4.0.5"
    configurePublishing = false
}

dependencies {
    implementation(project(":main"))
    compileOnly("com.github.topi314.lavasearch:lavasearch:1.0.0")
    implementation("com.github.topi314.lavasearch:lavasearch-plugin-api:1.0.0")
}
