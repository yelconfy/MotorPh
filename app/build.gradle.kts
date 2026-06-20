plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.4.2.jre11")
    implementation("com.opencsv:opencsv:5.9")
    implementation("com.kitfox.svg:svg-salamander:1.0")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("com.formdev:flatlaf:3.7.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

application {
    mainClass = "com.MotorPh.App"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
