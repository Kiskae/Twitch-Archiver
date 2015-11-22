package net.serverpeon.twitcharchiver.fx

import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.scene.Node
import javafx.scene.control.*
import javafx.util.Callback
import kotlin.reflect.KClass

fun <S, T> TableView<S>.column(
        label: String,
        value: S.() -> ObservableValue<T>,
        init: TableColumn<S, T>.() -> Unit = {}
): TableColumn<S, T> {
    val column = TableColumn<S, T>(label).apply {
        tooltip { label } //Set the tooltip to label by default
        isEditable = false
        cellValueFactory = KCallback { source ->
            source.value.value()
        }
        this.init()
    }
    column.cellFactory
    this.columns.add(column)
    return column
}

fun <T> T.toFxObservable(): ObservableValue<T> {
    return ReadOnlyObjectWrapper(this).readOnlyProperty
}

fun <S, T> TableColumn<S, T>.tooltip(label: () -> String) {
    val node = this.graphic
    when (node) {
        is Control -> node.tooltip = Tooltip(label())
        else -> {
            this.graphic = Label(this.text).apply {
                tooltip = Tooltip(label())
            }
            this.text = null
        }
    }
}

fun <S, T, N : Node> TableColumn<S, T>.renderer(
        @Suppress("UNUSED_PARAMETER") dummyClass: KClass<N>,
        render: TableCell<S, T>.(N?, T) -> N
) {
    this.cellFactory = KCallback {
        DelegateCell(render)
    }
}

fun <S, T> TableColumn<S, T>.textFormat(f: (T) -> String) {
    this.cellFactory = KCallback {
        SimpleCell(f)
    }
}

fun <S, T> TableColumn<S, T>.applyToCell(opts: TableCell<S, T>.() -> Unit) {
    val oldCellFactory = this.cellFactory
    this.cellFactory = KCallback { column ->
        oldCellFactory.call(column).apply(opts)
    }
}

private class SimpleCell<S, T>(val format: (T) -> String) : TableCell<S, T>() {
    override fun updateItem(item: T?, empty: Boolean) {
        super.updateItem(item, empty)

        if (item == null) {
            text = null
        } else {
            text = format(item)
        }
    }
}

private class DelegateCell<S, T, N : Node>(val render: TableCell<S, T>.(N?, T) -> N) : TableCell<S, T>() {

    init {
        text = null
    }

    override fun updateItem(item: T, empty: Boolean) {
        super.updateItem(item, empty)

        text = null
        if (empty) {
            graphic = null
        } else {
            @Suppress("UNCHECKED_CAST")
            val newGraphic = this.render(graphic as N?, item)
            if (newGraphic !== graphic) {
                graphic = newGraphic
            }
        }
    }
}

class KCallback<P, R>(val f: (P) -> R) : Callback<P, R> {
    override fun call(param: P): R {
        return f(param)
    }
}