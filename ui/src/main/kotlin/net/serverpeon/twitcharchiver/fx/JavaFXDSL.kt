package net.serverpeon.twitcharchiver.fx

import javafx.beans.binding.ObjectBinding
import javafx.beans.property.Property
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.control.Spinner
import javafx.scene.layout.*
import kotlin.properties.Delegates

fun Region.stretch(orientation: Orientation) {
    when (orientation) {
        Orientation.HORIZONTAL -> {
            HBox.setHgrow(this, Priority.ALWAYS)
            maxWidth = Double.MAX_VALUE
        }
        Orientation.VERTICAL -> {
            VBox.setVgrow(this, Priority.ALWAYS)
            maxHeight = Double.MAX_VALUE
        }
    }
}

fun <P, T> Property<P>.bindTransform(obs: ObservableValue<T>, func: (T) -> P) {
    this.bind(object : ObjectBinding<P>() {
        init {
            bind(obs)
        }

        override fun computeValue(): P {
            return func(obs.value)
        }
    })
}

fun Spinner<*>.makeEditable() {
    isEditable = true
    focusedProperty().addListener { obs ->
        try {
            val factory = valueFactory
            factory.value = factory.converter.fromString(editor.text)
        } catch (ignored: NumberFormatException) {
        }
    }
}

fun <P, T> transformObservable(obs: ObservableValue<T>, func: (T) -> P): ReadOnlyObjectProperty<P> {
    return SimpleObjectProperty<P>().apply {
        bindTransform(obs, func)
    }
}

fun vbox(block: NodeDSL<VBox>.() -> Unit): VBox {
    return fxDSL(VBox(), block)
}

fun hbox(block: NodeDSL<HBox>.() -> Unit): HBox {
    return fxDSL(HBox(), block)
}

fun <N : Node> buildNode(root: N,
                         appender: N.(Node) -> Unit,
                         children: N.() -> List<Node>,
                         block: NodeDSL<N>.() -> Unit): N {
    return root.apply {
        NodeDSL(this, appender, children).apply(block)
    }
}

fun <N : Pane> fxDSL(root: N, block: NodeDSL<N>.() -> Unit): N {
    return buildNode(root, { children.add(it) }, { children }, block)
}

class NodeDSL<N : Node>(val root: N, val appender: N.(Node) -> Unit, val children: N.() -> List<Node>) {

    fun init(init: N.() -> Unit) {
        root.init()
    }

    operator fun Node.unaryPlus() {
        root.appender(this)
    }

    inline fun <reified N : Node> forEach(func: N.() -> Unit) {
        root.children().forEach {
            if (it is N) {
                it.func()
            }
        }
    }
}

open class NodeDelegate() {
    protected var content: Node by Delegates.notNull()

    fun node() = content
}