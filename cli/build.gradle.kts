plugins { kotlin("jvm"); application }
dependencies {
    implementation(project(":core"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")
}
application { mainClass.set("assist.cli.MainKt"); applicationName = "proto-assist" }
