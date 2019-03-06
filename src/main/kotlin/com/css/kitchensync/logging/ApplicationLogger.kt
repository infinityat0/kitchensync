package com.css.kitchensync.logging

import com.typesafe.config.Config
import java.util.logging.LogManager
import java.util.logging.Logger

/**
 * Object that initializes and configures application level logging!
 */
object ApplicationLogger {

    private val logger = Logger.getLogger(this.javaClass.name)

    fun initialize(kitchenSyncConfig: Config) {
        try {
            val clientConfig = kitchenSyncConfig.getConfig("client")
            val logConfigFile = clientConfig.getString("log.log-properties")
            val url = this.javaClass.classLoader.getResource(logConfigFile)
            val stream = this.javaClass.classLoader.getResourceAsStream(logConfigFile)

            LogManager.getLogManager().readConfiguration(stream)
            logger.info("loaded logging properties from file: $url")
        } catch (ex: Exception) {
            logger.warning("Couldn't configure logger. Going with defaults. ex=$ex")
        }
    }
}