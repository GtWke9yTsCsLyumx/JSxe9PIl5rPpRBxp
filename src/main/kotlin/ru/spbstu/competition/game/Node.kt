package ru.spbstu.competition.game

enum class NodeStates { TWINER, ENEMY, NEUTRAL}

class Node(val id : Int) {
    var session = 0
    var prev : Node? = null
    var distance = 0
    var state = NodeStates.NEUTRAL
    val links = linkedSetOf<Node>()

    fun isBlocked() = this.state == NodeStates.ENEMY

    fun isNeutral() = this.state == NodeStates.NEUTRAL

    fun isTwiners() = this.state == NodeStates.TWINER

    fun changedAtSession(session : Int) = this.session == session

    fun resetInfo() {
        this.prev = null
        this.distance = Int.MAX_VALUE
    }

    fun updateInfo(calculationNum : Int, distance : Int, prev : Node) : Boolean {
        if((this.session != calculationNum || this.distance > distance)
                && !this.isBlocked()) {
            this.session = calculationNum
            this.distance = distance
            this.prev = prev
            return true
        }
        return false
    }
}

data class NodePair(val source : Node, val target : Node)