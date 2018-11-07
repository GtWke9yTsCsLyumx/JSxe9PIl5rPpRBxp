package ru.spbstu.competition.game

import ru.spbstu.competition.protocol.data.Claim
import ru.spbstu.competition.protocol.data.Setup
import java.util.*

class Graph(setup : Setup) {
    private val geenspoonId = setup.punter
    private val nodes = linkedMapOf<Int, Node>()
    private val mines = linkedSetOf<Node>()
    private val untappedMines = linkedSetOf<Node>()
    private val fullyCapturedMines = linkedSetOf<Node>()
    private var lastCalculation = 0
    private var lastEnemyNode : Node? = null
    private var currentNode : Node
    private var allDistantNeighborsCaptured = false

    init {
        // построение графа (добавление узлов и их связей)
        var n1 : Int
        var n2 : Int
        for(river in setup.map.rivers) {
            n1 = river.source
            n2 = river.target
            if(!nodes.containsKey(n1)) nodes[n1] = Node(n1)
            if(!nodes.containsKey(n2)) nodes[n2] = Node(n2)
            nodes[n1]!!.links.add(nodes[n2]!!)
            nodes[n2]!!.links.add(nodes[n1]!!)
        }

        // формирование списка майнеров
        for(mine in setup.map.mines) {
            mines.add(nodes[mine]!!)
            untappedMines.add(nodes[mine]!!)
        }

        // установка исходной точки пути
        currentNode = mines.last()
    }

    fun getCurrentNode() = currentNode

    // обновление состояний узлов графа после хода
    fun update(claim : Claim) {
        val n1 = claim.source
        val n2 = claim.target
        val newState : NodeStates
        if(claim.punter == geenspoonId) {
            newState = NodeStates.GREENSPOON
            lastEnemyNode = null
        }
        else {
            newState = NodeStates.ENEMY
            lastEnemyNode = nodes[n2]
        }
        if(!mines.contains(nodes[n1])) nodes[n1]?.state = newState
        if(!mines.contains(nodes[n2])) nodes[n2]?.state = newState
    }

    // вычисление расстояний от текущей точки до всех точек графа
    // записывает информацию на узлы графа
    // (алгоритм Дейкстры)
    private fun calculateDistances()  {
        val calculationNum = lastCalculation + 1
        val queue = PriorityQueue<Node>()
        this.currentNode.distance = 0
        var currentNode = this.currentNode
        queue.add(currentNode)
        while(queue.isNotEmpty()) {
            currentNode = queue.poll()
            for(neighbour in currentNode.links) {
                if(neighbour.updateInfo(calculationNum,
                                currentNode.distance + 1,
                                currentNode)) {
                    queue.add(neighbour)
                }
            }
        }
        lastCalculation = calculationNum
    }

    // получение узла для следующего хода
    fun getNextNode() : Node? {
        val trick = this.makeATrick()
        return when {
            trick != null -> trick
            !this.untappedMines.isEmpty() -> getNextNode1()
            fullyCapturedMines.size != mines.size -> getNextNode2()
            else -> getNextNode3()
        }
    }

    // получение следующего узла кратчайшего пути до ближайщего майнера
    private fun getNextNode1() : Node {

        // Дейкстра
        this.calculateDistances()

        // определение ближайшего майнера
        var nearestMine = untappedMines.first()
        for (mine in untappedMines)
            if (mine.distance < nearestMine.distance)
                nearestMine = mine

        // "развертываение" пути до узла, следующего после текущего
        var currentNode = this.currentNode
        while (currentNode.prev!! != this.currentNode)
            currentNode = currentNode.prev!!

        // удаление майнера из списка "нетронутых" (если это майнер)
        untappedMines.remove(currentNode.prev!!)

        // возврат узла для для хода
        return currentNode.prev!!
    }


    // если все майнеры были захвачены, но не были "захвачены полностью"
    // получение соседнего узла одного из майнеров
    // тактика: захват всех соседних узлов каждого майнера
    private fun getNextNode2() : Node {
        for (mine in mines) {
            for (neighbour in mine.links)
                if (neighbour.isNeutral()) return neighbour
            fullyCapturedMines.add(mine)
        }

    }

    // получение "дальнего соседа" майнера (сосед соседа)
    // если все "дальние соседи" захвачены
    private fun getNextNode3() : Node? {
        if(!allDistantNeighborsCaptured) {
            for (neighbour in mines)
                for (distantNeighbor in neighbour.links)
                    if (distantNeighbor.isNeutral()) return distantNeighbor
            allDistantNeighborsCaptured = true
            return getNextNode3()
        }
        else {
            for(node in nodes)
                if(node.value.isNeutral()) return node.value
        }
        return null
    }

    //
    private fun makeATrick() : Node? {
        // метод будет дописан на основании того, как действуют боты соперников
        return null
    }
}