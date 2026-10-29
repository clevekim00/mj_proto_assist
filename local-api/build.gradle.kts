plugins { kotlin("jvm"); application }
dependencies {
    implementation(project(":core"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.7"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
application { mainClass.set("assist.web.ApplicationKt") }
tasks.processResources { from("../ui/dist") { into("static") } }
