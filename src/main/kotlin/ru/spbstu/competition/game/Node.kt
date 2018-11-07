package ru.spbstu.competition.game

enum class NodeStates { GREENSPOON, ENEMY, NEUTRAL}

class Node(val id : Int) {
    var calculationNum : Int? = null
    var prev : Node? = null
    var distance = 0
    var state = NodeStates.NEUTRAL
    val links = linkedSetOf<Node>()

    fun isBlocked() = this.state == NodeStates.ENEMY

    fun isNeutral() = this.state == NodeStates.NEUTRAL

    fun updateInfo(calculationNum : Int, distance : Int, prev : Node) : Boolean {
        if((this.calculationNum != calculationNum || this.distance > distance)
                && !this.isBlocked()) {
            this.calculationNum = calculationNum
            this.distance = distance
            this.prev = prev
            return true
        }
        return false
    }
}