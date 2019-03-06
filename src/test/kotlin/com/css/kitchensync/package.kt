package com.css.kitchensync

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

fun testConfig(): Config =
    ConfigFactory.load().getConfig("test").getConfig("kitchensync")