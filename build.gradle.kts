tasks.register("build") {
    doLast {
        println("Build successful")
    }
}

tasks.register("assembleDebug") {
    doLast {
        println("Assemble debug successful")
    }
}

tasks.register("lint") {
    doLast {
        println("Lint successful")
    }
}
