package com.css.kitchensync.common

data class OrderStatus(val name: String, val temp: String, val value: Float, val normalizedValue: Float) {
    fun toJson() = Instances.jsonParser.toJsonString(this)
}

data class ShelfStatus(val shelfName: String, val orderStatus: List<OrderStatus>) {
    fun toJson() = Instances.jsonParser.toJsonString(this)

    fun isEmpty() = orderStatus.isEmpty()
}
