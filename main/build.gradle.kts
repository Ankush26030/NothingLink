plugins {
    `java-library`
}

base {
    archivesName = "amazonmusic-main"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

dependencies {
    api("com.github.topi314.lavasearch:lavasearch:1.0.0")
    compileOnly("dev.arbjerg:lavaplayer:2.0.4")
    implementation("commons-io:commons-io:2.7")
    compileOnly("org.slf4j:slf4j-api:2.0.7")
    compileOnly("org.jetbrains:annotations:24.0.0")
}
