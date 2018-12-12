package ru.spbstu.competition.game

import ru.spbstu.competition.protocol.data.Claim
import ru.spbstu.competition.protocol.data.Setup
import java.util.*

class Graph(setup : Setup) {
    private val twinerId = setup.punter
    private val nodes = linkedMapOf<Int, Node>()
    private val mines = linkedSetOf<Node>()
    private val unlinkedMines = linkedSetOf<Node>()
    private val unrealisedMines = linkedSetOf<Node>()
    private val twinerPairs = hashSetOf<NodePair>()
    private val reachedNodesList = linkedSetOf<Node>()
    private var stage3IsStarted = false
    private var lastMove = NodePair(Node(-1), Node(-2))
    private var lastSession = 0
    private var moveNum = 1
    private var methodNum = 0
    private var enemyLogs = mutableListOf<String>()

    init {
        println("\tgraph building")
        // graph building
        var n1 : Int
        var n2 : Int

        println("\t\tcreating nodes")
        for(river in setup.map.rivers) {
            n1 = river.source
            n2 = river.target
            if(!nodes.containsKey(n1)) nodes[n1] = Node(n1)
            if(!nodes.containsKey(n2)) nodes[n2] = Node(n2)
            nodes[n1]!!.links.add(nodes[n2]!!)
            nodes[n2]!!.links.add(nodes[n1]!!)
        }

        // creating mines lists
        println("\t\tcreating list of mines")
        for(mineId in setup.map.mines) {
            mines.add(nodes[mineId]!!)
            unlinkedMines.add(nodes[mineId]!!)
            unrealisedMines.add(nodes[mineId]!!)
        }

        println("\t\tgraph building completed!")
        println("\t\t\tnodes: ${nodes.size}\n\t\t\tmines: ${mines.size}")
        print("\t\t\tmine ids : [")
        mines.forEach { print(" ${it.id}") }
        println(" ]")
    }

    fun saveLastMove(movePair : NodePair) {
        lastMove = movePair
    }

    fun getMethodNum() = methodNum

    fun nodeIsMine(node : Node) = mines.contains(node)

    fun createReachedNodesList() {
        twinerPairs.forEach {
            reachedNodesList.add(it.node1)
            reachedNodesList.add(it.node2)
        }
    }

    // обновление графа после хода
    fun update(claim : Claim) {
        val n1 = nodes[claim.source]!!
        val n2 = nodes[claim.target]!!
        val movePair = NodePair(n1, n2)
        if(claim.punter == twinerId) {
            twinerPairs.add(movePair)

            if(lastMove.node1.id != claim.source ||
                    lastMove.node2.id != claim.target)
                println("\nTWINER IN ASTRAL!\n\n\n\n\n\n\n\n")

            print("\tresult: (${claim.source} -> ${claim.target})")
            println(" ${n1.id} -> ${n2.id}")

            println()
            for(log in enemyLogs) println(log)
            println()
            enemyLogs.clear()

            if(stage3IsStarted) {
                reachedNodesList.add(n1)
                reachedNodesList.add(n2)
            }

            this.moveNum++
        }
        else {
            this.removeLink(n1, n2)
            val enemyLog = "OPPONENT'S MOVE: ${claim.source} -> ${claim.target} (player ${claim.punter})"
            enemyLogs.add(enemyLog)
        }
    }

    // reset information of each node in graph
    private fun resetDistances() {
        nodes.values.forEach { it.resetInfo() }
    }

    // returns true if pair of nodes is captured by Twiner
    fun pairCaptured(node1 : Node, node2 : Node) =
            twinerPairs.contains(NodePair(node1, node2))

    // completely removes node from graph
    private fun removeNode(node : Node) {
        // removing all node links
        for(neighbour in node.links) neighbour.links.remove(node)

        // removing from the main node list
        nodes.remove(node.id)

        // removing from mine lists if it is mine
        if(nodeIsMine(node)) {
            mines.remove(node)
            unlinkedMines.remove(node)
            unrealisedMines.remove(node)
        }

        // removing from reached nodes list on the third stage
        if(stage3IsStarted) reachedNodesList.remove(node)
    }

    // completely removes link between two nodes
    private fun removeLink(node1 : Node, node2 : Node) {
        node1.links.remove(node2)
        node2.links.remove(node1)
        if(node1.links.isEmpty()) this.removeNode(node1)
        if(node2.links.isEmpty()) this.removeNode(node2)
    }

    // returns a farthest node from source node
    private fun getFarthestNodeFrom(source : Node) : Node {
        resetDistances()
        val sessionNum = lastSession + 1
        val queue = LinkedList<Node>()
        var farthestNode = Node(-1)
        farthestNode.distance = Int.MIN_VALUE

        // Dijkstra
        source.distance = 0
        var currentNode = source
        queue.add(currentNode)
        while(queue.isNotEmpty()) {
            currentNode = queue.poll()
            for(neighbour in currentNode.links) {
                if(neighbour.updateInfo(sessionNum, currentNode.distance + 1, currentNode)) {
                    if (neighbour.distance > farthestNode.distance)
                        farthestNode = neighbour
                    queue.add(neighbour)
                }
            }
        }
        lastSession = sessionNum
        return farthestNode
    }

    // returns a nearest mine from a source node
    private fun nearestMineFrom(source : Node) : Node? {
        resetDistances() // !!!
        val sessionNum = lastSession + 1
        val queue = LinkedList<Node>()
        var nearestMine = Node(Int.MAX_VALUE)
        val unreviewedMines = linkedSetOf<Node>()
        mines.map { unreviewedMines.add(it) }
        unreviewedMines.remove(source)
        nearestMine.distance = Int.MAX_VALUE

        // Dijkstra
        source.distance = 0
        var currentNode = source
        queue.add(currentNode)
        while(queue.isNotEmpty() && unreviewedMines.isNotEmpty()) {
            currentNode = queue.poll()
            for(neighbour in currentNode.links) {
                if(neighbour.updateInfo(sessionNum,currentNode.distance + 1, currentNode)) {
                    if (unreviewedMines.contains(neighbour) && neighbour.distance < nearestMine.distance) {
                        nearestMine = neighbour
                        unreviewedMines.remove(neighbour) // removing from unreviewed mines list
                    }
                    queue.add(neighbour)
                }
            }
        }
        lastSession = sessionNum

        // if there's no reachable mines
        if(nearestMine.distance == Int.MAX_VALUE) return null

        return nearestMine
    }

    // getting node for a next move
    // stage 1 - linking mines
    fun getNextNode() : NodePair? {
        println("-> stage 1")
        methodNum = 1 // method mark

        // if there's no more unlinked mines
        if(unlinkedMines.isEmpty()){
            println("\t\tall the mines are linked")
            println("\t\t-> stage 2")
            return getNextNode2() // go to the next stage
        }

        val mine = unlinkedMines.first() // source mine
        println("\t\tsource mine - ${mine.id}")
        println("\t\tgetting a nearest mine")
        val targetNode = nearestMineFrom(mine) // defining nearest mine from source

        // if there's no reachable mines
        if(targetNode == null) {
            println("\t\tsource mine is detached from other mines")
            unlinkedMines.remove(mine) // removing mine from unlinked mines list
            println("\t\tsource mine has been removed from unlinked mines list")
            println("\t\t^ repeat stage 1")
            return getNextNode() // repeat method call
        }

        println("\t\tnearest mine - ${targetNode.id}")

        // getting last node of path
        println("\t\tdefining next node of path")
        val lastPathNode = unrollPath(mine, targetNode)

        // if path unrolled to the source mine
        if(lastPathNode == mine) {
            println("\t\tsource mine already linked with nearest")
            unlinkedMines.remove(mine)
            println("\t\tsource mine has been removed from unlinked mines list")
            println("\t\t^ repeat stage 1")
            return getNextNode() // repeat method call
        }

        // getting next node
        val nextNode = lastPathNode.prev!!
        println("\t\tnext node defined - ${nextNode.id}")

        if(nextNode == mine) {
            println("\t\tmines are linked")
            unlinkedMines.remove(mine)
            println("\t\tsource mine has been removed from unlinked mines list")
        }

        return NodePair(lastPathNode, nextNode)
    }

    // returns next node of path from target to source
    private fun unrollPath(source : Node, target : Node) : Node {
        var currentNode = target
        while(currentNode != source && pairCaptured(currentNode, currentNode.prev!!)) {
            currentNode = currentNode.prev!!
        }
        return currentNode
    }

    // returns next node of path from source to target
    private fun unrollReversedPath(source : Node, target : Node) : Node {
        var currentNode = target
        var path = mutableListOf<Node>()

        while(currentNode != source) {
            path.add(currentNode)
            currentNode = currentNode.prev!!
        }
        path.add(source)
        path = path.asReversed()

        var i = 1
        while(i < path.size - 1 && pairCaptured(path[i - 1], path[i])) i++
        return path[i]
    }

    // getting node for a next move
    // stage 2 - linking mines with their farthest nodes
    private fun getNextNode2() : NodePair? {
        this.methodNum = 2 // method mark

        // if all the mines are already realised
        if(unrealisedMines.isEmpty()){
            println("\t\t\tall the mines are realised")
            println("\t\t\t-> stage 3")
            return getNextNode3() // go to the stage 3
        }

        // getting a farthest node
        val mine = unrealisedMines.first() // source mine
        println("\t\t\tsource mine - ${ mine.id }")
        println("\t\t\tgetting a farthest node")
        val farthestNode = getFarthestNodeFrom(mine)
        println("\t\t\tfarthest node - ${ farthestNode.id }")

        if(farthestNode == mine) {
            println("\t\t\tsource mine isolated")
            unrealisedMines.remove(mine)
            println("\t\t\tsource mine has been removed from unrealised mines list")
            println("\t\t\t^ repeat stage 2")
            return getNextNode2() // repeat method call
        }

        // defining next node of path to farthes node
        println("\t\t\tdefining next node of path from source")
        val nextNode = unrollReversedPath(mine, farthestNode)

        // if this move will linked miner with it's farthest node
        if(nextNode == farthestNode) {
            if(pairCaptured(nextNode, nextNode.prev!!)) {
                println("\t\t\tthe mine is already linked with farthest node")
                unrealisedMines.remove(mine)
                println("\t\t\tsource mine has been removed from unrealised mines list")
                println("\t\t\t^ repeat stage 2")
                return getNextNode2() // repeat method call
            }
            println("\t\t\tsource mine has been linked with farthest node")
            unrealisedMines.remove(mine)
            println("\t\t\tsource mine has been removed from unrealised mines list")
        }

        return NodePair(nextNode.prev!!, nextNode)
    }

    // getting node for a next move
    // stage 3 - capturing remained pairs
    private fun getNextNode3() : NodePair? {
        this.methodNum = 3 // method mark

        // creating reached nodes list if it needed
        if(!stage3IsStarted) {
            println("\t\t\t\tcreating reached nodes list")
            createReachedNodesList()
            stage3IsStarted = true
        }

        // searching of unreached node
        println("\t\t\t\tgetting unreached node")
        for(node in reachedNodesList) {
            for(neighbour in node.links) {
                if(!reachedNodesList.contains(neighbour)) {
                    println("\t\t\t\tunreached node - ${node.id}")
                    return NodePair(node, neighbour)
                }
            }
        }

        // if Twiner can't find node pair for move
        println("\t\t\t\tthere are no more reachable nodes")
        return null
    }
}