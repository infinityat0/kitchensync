package com.css.kitchensync.common

import com.beust.klaxon.Klaxon

data class OrderStatus(val name: String, val temp: String, val value: Float, val normalizedValue: Float) {
  fun toJson() = Klaxon().toJsonString(this)
}

data class ShelfStatus(val shelfName: String, val orderStatus: List<OrderStatus>) {
  fun toJson() = Klaxon().toJsonString(this)
}
