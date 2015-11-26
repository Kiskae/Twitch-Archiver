package net.serverpeon.twitcharchiver.fx

import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
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

fun vbox(block: NodeDSL<VBox>.() -> Unit): VBox {
    return buildNode(VBox(), { children.add(it) }, { children }, block)
}

fun hbox(block: NodeDSL<HBox>.() -> Unit): HBox {
    return buildNode(HBox(), { children.add(it) }, { children }, block)
}

fun <N : Node> buildNode(root: N,
                         appender: N.(Node) -> Unit,
                         children: N.() -> List<Node>,
                         block: NodeDSL<N>.() -> Unit): N {
    return root.apply {
        NodeDSL(this, appender, children).apply(block)
    }
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