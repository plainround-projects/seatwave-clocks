plugins {
    java
    jacoco
    alias(libs.plugins.spotless)
}

group = "io.plainround"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // The clocks and CRDTs need only the JDK; the tests need JUnit and AssertJ.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Transitive versions are written to gradle.lockfile, so two builds can never resolve different jars.
dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    // Every warning is an error: a warning that is allowed to stay is a defect nobody owns.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

spotless {
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
        endWithNewline()
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    finalizedBy(tasks.jacocoTestReport)
}

// The programs (*Cli) are run by hand and their output is printed in the README and the module.
tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude("**/*Cli*") })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// One task per program, so a reader runs one command per example.
mapOf(
    "skewRun" to ("io.plainround.seatwave.clocks.ClockSkewCli" to "Last-writer-wins by wall clock under skew, and what a hybrid clock does instead"),
    "orderingRun" to ("io.plainround.seatwave.clocks.OrderingCli" to "Lamport clocks, hybrid clocks and vector clocks on the same histories"),
    "cartRun" to ("io.plainround.seatwave.clocks.CartConflictCli" to "Concurrent cart versions: siblings, the context and the observed-remove merge"),
    "counterRun" to ("io.plainround.seatwave.clocks.CounterCli" to "G-Counter and PN-Counter convergence in every delivery order"),
).forEach { (taskName, program) ->
    tasks.register<JavaExec>(taskName) {
        group = "seatwave"
        description = program.second
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = program.first
    }
}
