package com.css.kitchensync

import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.runBlocking

private fun doHouseKeeping() = runBlocking {
  // start a ticker that ticks every second after the first second.
  val tickerChannel = ticker(delayMillis = 1000, initialDelayMillis = 1000)
  for (tick in tickerChannel) {

  }
}
