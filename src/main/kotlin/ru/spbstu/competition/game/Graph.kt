package ru.spbstu.competition.game

import ru.spbstu.competition.protocol.data.Claim
import ru.spbstu.competition.protocol.data.Setup
import java.util.*

class Graph(setup : Setup) {
    private val twinerId = setup.punter
    private val nodes = linkedMapOf<Int, Node>()
    private val mines = linkedSetOf<Node>()
    private val untappedMines = linkedSetOf<Node>()
    private val fullyCapturedMines = linkedSetOf<Node>()
    private var lastSession = 0
    private var lastEnemyNode : Node? = null
    private var currentNode : Node
    private var allDistantNeighborsCaptured = false
    private var moveNum = 1
    private var methodNum = 0

    init {
        println("Graph building")
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

        // установка отправной точки
        this.currentNode = mines.last()

        // удаление отправной точки из списка "нетронутых" майнеров
        untappedMines.remove(this.currentNode)

        println("\tcomplete!")
    }

    fun getCurrentNode() = currentNode
    fun getMethodNum() = methodNum

    fun nodeIsMiner(node : Node) = mines.contains(node)

    // обновление состояний узлов графа после хода
    fun update(claim : Claim) {
        val n1 = claim.source
        val n2 = claim.target
        val newState : NodeStates
        if(claim.punter == twinerId) {
            newState = NodeStates.TWINER
//            println("\t${this.moveNum}) ${this.currentNode.id} -> ${nodes[n2]!!.id} (${this.methodNum})") // !!!
            println("\tresult:  ${this.currentNode.id} -> ${nodes[n2]!!.id}")
            this.currentNode = nodes[n2]!! // !!!
            this.moveNum++
            lastEnemyNode = null
        }
        else {
            newState = NodeStates.ENEMY
            lastEnemyNode = nodes[n2]
        }
        if(!mines.contains(nodes[n1])) nodes[n1]?.state = newState
        if(!mines.contains(nodes[n2])) nodes[n2]?.state = newState
    }

    // очистка просчитанных путей
    private fun resetDistances() {
        nodes.values.forEach { it.resetInfo() }
    }

    // вычисление расстояний от текущей точки до всех точек графа
    // записывает информацию на узлы графа
    // (алгоритм Дейкстры)
    private fun calculateDistances()  {
        resetDistances() // !!!
        val calculationNum = lastSession + 1
        val queue = LinkedList<Node>()
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
        lastSession = calculationNum
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
    private fun getNextNode1() : Node? {
        // маркер метода
        this.methodNum = 1

        // Дейкстра
        this.calculateDistances()

        // в случае отсутствия нетронутых майнеров
        // (возможно, если метод был вызван в результате рекурсии)
        // происходит выполнение второго варианта поиска хода
        if(untappedMines.isEmpty()) return getNextNode2()

        // определение ближайшего майнера
        var nearestMine = Node(Int.MAX_VALUE) // фиктивный "дальнейший" узел
        nearestMine.distance = Int.MAX_VALUE // (максимальная дистанция)
        for (mine in untappedMines) {
            // если расстояние до данного майнера было найдено во время
            // последней сессии и это расстояние короче...
            if (mine.changedAtSession(lastSession) &&
                    mine.distance < nearestMine.distance)
                nearestMine = mine
        }

        // если нет майнеров, до которых можно добраться
        // из текущий точки
        if(nearestMine.id == Int.MAX_VALUE) { // (фективный узел в результате)
            this.currentNode = nearestMine // "прыжок" к следующему нетронутому майнеру
            untappedMines.remove(this.currentNode) // удаление майнера его их списка нетронутых
            println("\tjump on ${untappedMines.size}")
            return getNextNode1() // повторный вызов метода
        }

        // "развертываение" пути от ближ. майнера до первого захваченного узла,
        // либо до узла, следующего за текущим
        var currentNode = nearestMine
//        println("begin while")
        while (currentNode.prev!! != this.currentNode &&
                !currentNode.prev!!.isTwiners())
            currentNode = currentNode.prev!!
//        println("end while")

        // если за узлом, на котором остановилась развертка, следует не текущий узел,
        // то "переход" на него (изменение текущего узла)
        if(currentNode.prev != this.currentNode)
            this.currentNode = currentNode.prev!!

        // удаление майнера из списка "нетронутых" (если это майнер)
        untappedMines.remove(currentNode.prev!!)

        // возврат узла для для хода
        return currentNode
    }


    // если все майнеры были захвачены, но не были "захвачены полностью"
    // получение соседнего узла одного из майнеров
    // тактика: захват всех соседних узлов каждого майнера
    private fun getNextNode2() : Node? {
        // маркер метода
        this.methodNum = 2

        for (mine in mines) {
            for (neighbour in mine.links)
                if (neighbour.isNeutral()) return neighbour
            fullyCapturedMines.add(mine)
        }
        return null
    }

    // получение "дальнего соседа" майнера (сосед соседа)
    // если все "соседи" захвачены
    private fun getNextNode3() : Node? {
        // маркер метода
        this.methodNum = 3

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