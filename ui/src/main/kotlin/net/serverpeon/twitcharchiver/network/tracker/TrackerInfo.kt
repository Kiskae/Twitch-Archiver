package net.serverpeon.twitcharchiver.network.tracker

import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty

data class TrackerInfo(val hasPriorData: Boolean = false) {
    private val downloadProgress = SimpleDoubleProperty(0.0)
    val downloadProgressProp: ReadOnlyDoubleProperty
        get() = downloadProgress

    private val downloadedParts = SimpleIntegerProperty(0)
    val downloadedPartsProp: ReadOnlyIntegerProperty
        get() = downloadedParts

    private val failedParts = SimpleIntegerProperty(0)
    val failedPartsProp: ReadOnlyIntegerProperty
        get() = failedParts
}