package com.css.kitchensync.common

data class OrderStatus(val id: String, val name: String, val shelf: String, val temp: String, val value: Float, val normalizedValue: Float) {

    fun toJson() = Instances.jsonParser.toJsonString(this)
}

data class ShelfStatus(val shelfName: String, val orderStatus: List<OrderStatus>) {
    fun toJson() = Instances.jsonParser.toJsonString(this)

    fun isEmpty() = orderStatus.isEmpty()
}

interface Message {
    fun toJson() = Instances.jsonParser.toJsonString(this)
}

class AddOrder(
    val shelf: String,
    val orderStatus: OrderStatus,
    val action: String = "add-order"): Message

class RemoveOrder(
    val orderStatus: OrderStatus,
    val action: String = "remove-order"): Message

class MovedOrder(
    val fromShelf: String,
    val toShelf: String,
    val orderStatus: OrderStatus,
    val action: String = "move-order"): Message

class UpdateValue(
    val orderStatus: OrderStatus,
    val shelf: String,
    val action: String = "update-value"): Message

