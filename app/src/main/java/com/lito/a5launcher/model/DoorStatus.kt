package com.lito.a5launcher.model

data class DoorStatus(
    val driverOpen: Boolean = false,
    val passengerOpen: Boolean = false,
    val rearLeftOpen: Boolean = false,
    val rearRightOpen: Boolean = false,
    val hoodOpen: Boolean = false,
    val trunkOpen: Boolean = false,
)
