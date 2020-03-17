package hl7.transformer

import io.micronaut.runtime.Micronaut

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.build()
                .packages("hl7.transformer")
                .mainClass(Application.javaClass)
                .start()
    }
}