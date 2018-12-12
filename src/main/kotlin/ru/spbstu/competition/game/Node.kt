package ru.spbstu.competition.game

enum class NodeStates { TWINER, ENEMY, NEUTRAL}

class Node(val id : Int) {
    var session = 0
    var prev : Node? = null
    var distance = 0
    val links = linkedSetOf<Node>()

    fun resetInfo() {
        this.prev = null
        this.distance = Int.MAX_VALUE
    }

    fun updateInfo(currentSession : Int, distance : Int, prev : Node) : Boolean {
        if((this.session != currentSession || this.distance > distance)) {
            this.session = currentSession
            this.distance = distance
            this.prev = prev
            return true
        }
        return false
    }
}

data class NodePair(val node1 : Node, val node2 : Node) {

    override fun equals(other: Any?) = other is NodePair &&
            ( other.node1 == node1 && other.node2 == node2 ||
              other.node2 == node1 && other.node1 == node2 )

    override fun hashCode() = node1.id.hashCode() xor node2.id.hashCode()
}