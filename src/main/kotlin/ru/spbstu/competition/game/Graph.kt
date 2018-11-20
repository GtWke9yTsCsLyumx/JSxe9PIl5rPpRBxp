package ru.spbstu.competition.game

import ru.spbstu.competition.protocol.data.Claim
import ru.spbstu.competition.protocol.data.Setup
import java.util.*
import kotlin.collections.LinkedHashSet

class Graph(setup : Setup) {
    private val twinerId = setup.punter
    private val nodes = linkedMapOf<Int, Node>()
    private val mines = linkedSetOf<Node>()
    private val neutralMines : LinkedHashSet<Node>
    private val detachedMines = linkedSetOf<Node>()
    private val capturedMines = linkedSetOf<Node>()
    private val unrealisedMines = linkedSetOf<Node>()
    private var lastMove = NodePair(Node(-1), Node(-2))
    private var lastSession = 0
    private var lastEnemyNode : Node? = null
    private var moveNum = 1
    private var methodNum = 0

    init {
        println("\tgraph building")
        // построение графа (добавление узлов и их связей)
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

        println("\t\tcreating ordered list of mines")
        // запись узлов-майнеров в буферный список,
        // запись в такой же буфер максимальных дистанций от них
        var mineBuf : Node
        val minesBuf = mutableListOf<Node>()
        val minesMaxDistances = mutableListOf<Int>()
        for(mineId in setup.map.mines) {
            mineBuf = nodes[mineId]!! // получение узла-майнера по id
            minesBuf.add(mineBuf) // запись майнера в буфер
            minesMaxDistances.add(furthestNodeFrom(mineBuf)!!.distance) // запись максимальной дистанции от майнера
        }

        // формирование списка майнеров, отсортированного
        // по расстояниям до дальнейших точек
        var maxDistance : Int
        var maxDistanceIndex = Int.MIN_VALUE
        while(minesBuf.isNotEmpty()) {
            maxDistance = Int.MIN_VALUE
            for(i in 0 until minesBuf.size) {
                if(minesMaxDistances[i] > maxDistance) {
                    maxDistance = minesMaxDistances[i]
                    maxDistanceIndex = i
                }
            }
            mines.add(minesBuf[maxDistanceIndex])
            minesBuf.removeAt(maxDistanceIndex)
            minesMaxDistances.removeAt(maxDistanceIndex)
        }

        // инициализация списка свободных майнеров (незахваченных)
        neutralMines = mines

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

    fun nodeIsMiner(node : Node) = mines.contains(node)

    // обновление графа после хода
    fun update(claim : Claim) {
        val n1 = nodes[claim.source]!!
        val n2 = nodes[claim.target]!!
        if(claim.punter == twinerId) {
            n1.state = NodeStates.TWINER
            n2.state = NodeStates.TWINER
            if(lastMove.source.id != claim.source ||
                    lastMove.target.id != claim.target)
                println("\nTWINER CONTINUE TO GROW IN ASTRAL!\n\n\n\n\n\n\n\n")
            println("\tresult (original) : ${claim.source} -> ${claim.target}")
            println("\tresult:  ${n1.id} -> ${n2.id}")
            this.moveNum++
        }
        else {
            n1.state = NodeStates.ENEMY
            n2.state = NodeStates.ENEMY
            lastEnemyNode = n2
            neutralMines.remove(n2)
        }
    }

    // очистка вычисленных путей (сброс информации на каждом узле)
    private fun resetDistances() {
        nodes.values.forEach { it.resetInfo() }
    }

    // получение самого удаленного СВОБОДНОГО узла от переданного
    private fun furthestNodeFrom(source : Node) : Node? {
        resetDistances() // !!!
        val sessionNum = lastSession + 1
        val queue = LinkedList<Node>()
        var farthestNode = Node(-1)
        farthestNode.distance = Int.MIN_VALUE

        // Дейкстра
        source.distance = 0
        var currentNode = source
        queue.add(currentNode)
        while(queue.isNotEmpty()) {
            currentNode = queue.poll()
            for(neighbour in currentNode.links) {
                if(neighbour.updateInfo(sessionNum,
                                currentNode.distance + 1,
                                currentNode)) {
                    if(neighbour.isNeutral() &&
                            neighbour.distance > farthestNode.distance)
                        farthestNode = neighbour
                    queue.add(neighbour)
                }
            }
        }
        lastSession = sessionNum
        if(farthestNode.id == -1) return null
        return farthestNode
    }

    // определение ближайшего СВОБОДНОГО майнера для переданного узла
    private fun nearestMineFrom(source : Node) : Node? {
        resetDistances() // !!!
        val sessionNum = lastSession + 1
        val queue = LinkedList<Node>()
        var nearestMine = Node(Int.MAX_VALUE)
        val unreviewedMines = mines
        nearestMine.distance = Int.MAX_VALUE

        // Дейкстра
        source.distance = 0
        var currentNode = source
        queue.add(currentNode)
        while(queue.isNotEmpty() && unreviewedMines.isNotEmpty()) {
            currentNode = queue.poll()
            for(neighbour in currentNode.links) {
                if(neighbour.updateInfo(sessionNum,
                                currentNode.distance + 1,
                                currentNode)) {
                    if(nodeIsMiner(neighbour) && neighbour.isNeutral()) { // если это свободный майнер
                        if(neighbour.distance < nearestMine.distance) // если он ближайший
                            nearestMine = neighbour
                        unreviewedMines.remove(neighbour) // удаление из списка нерассмотренных
                    }
                    queue.add(neighbour)
                }
            }
        }
        lastSession = sessionNum
        if(nearestMine.distance == Int.MAX_VALUE) return null // если нет досягаемых майнеров
        return nearestMine
    }

    // получение узла для следующего хода
    // первый этап - захват майнеров в порядке значимости
    // посредством "шагов" в направлении ближайших майнеров
    fun getNextNode() : NodePair? {
        methodNum = 1 // маркер метода

        // если не осталось незахваченных майнеров
        if(neutralMines.isEmpty()){
            println("\t\tall neutral miners are captured")
            println("\t\tgoing to the stage 2")
            return getNextNode2()
        } // переход к след. этапу

        println("\t\tgetting a nearest mine")
        val mine = neutralMines.first() // исходный майнер
        var targetNode = nearestMineFrom(mine) // определение ближайшего майнера для текущего

        if(targetNode == null) { // если нет досягаемых майнеров
            // определение самого дальнего узла
            // (на основании уже вычисленных путей)
            println("\t\tmine is isolated from other mines")
            println("\t\tfarthest node searching")
            var farthestNode = Node(-1) // фиктивный начальный узел
            farthestNode.distance = -1
            for(node in nodes.values) {
                if(node.session == lastSession &&
                        node.distance > farthestNode.distance)
                    farthestNode = node
            }
            // если майнер оказался изолированным
            if(farthestNode.id == -1) {
                println("\t\tmine is isolated")
                neutralMines.remove(mine) // удаление из списка
                println("\trepeat $methodNum")
                return getNextNode() // повторный вызов метода
            }
            // установка самого дальнего узла в качестве цели
            targetNode = farthestNode
        }

        // возврат следующего узла на пути к цели
        val nextNode = nextPathNode(mine, targetNode) // след. узел

        // если следующий узел - майнер
        // значит, захвачено сразу 2 майнера
        if(nodeIsMiner(nextNode)) {
            println("\t\ttarget node is mine")
            println("\t\ttwo mines will be captured")
            // обновление списков:
            neutralMines.remove(nextNode) // список свободных (-)
            capturedMines.add(nextNode) // список захваченных (+)
            detachedMines.add(nextNode) // список обособленных (+)
            unrealisedMines.add(nextNode) // список нереализованных (+)
        }

        // обновление списков:
        neutralMines.remove(mine) // список свободных (-)
        capturedMines.add(mine) // список захваченных (+)
        detachedMines.add(mine) // список обособленных (+)
        unrealisedMines.add(mine) // список нереализованных (+)

        return NodePair(nextNode.prev!!, nextNode)
    }

    // получение следующего узла пути от исходного манера к целевому
    private fun nextPathNode(source : Node, target : Node) : Node {
        var currentNode = target
        var prevNode = currentNode.prev
        while(currentNode.isNeutral() &&
                (prevNode == source || prevNode!!.isTwiners())) {
            currentNode = prevNode
            prevNode = prevNode.prev
        }
        return currentNode
    }

    // получение узла для следующего хода
    // второй этап - соединение захваченных майнеров между собой
    private fun getNextNode2() : NodePair? {
        this.methodNum = 2 // маркер метода

        // если не осталось обособленных майнеров (которые могут быть соединены)
        if(detachedMines.isEmpty())  {
            println("\t\t\tthere's no more detached mines")
            println("\t\t\tgoing to the stage 3")
            return getNextNode3() // переход к этапу 3
        }

        println("\t\t\tgetting a nearest mine")
        val mine = detachedMines.first() // исходный майнер
        val nearestMine = nearestMineFrom(mine) // определение ближайшего майнера

        // если майнер изолирован от других майнеров
        if(nearestMine == null) {
            println("\t\t\tminer is isolated")
            println("\t\t\tgoing to the stage 3")
            detachedMines.remove(mine) // удаление из списка обособленных
            return getNextNode3() // переход к этапу 3
        }

        // определение следующего узла на пути к ближайшему майнеру
        val nextNode = nextPathNode(mine, nearestMine)

        // если путь развернулся до исходного майнера,
        // значит они уже соединены
        if(nextNode == nearestMine) {
            detachedMines.remove(mine) // удаление майнера из списка обособленных
            println("\t\t\tthis mine already connected with this nearest")
            println("\t\t\trepeat $methodNum")
            getNextNode2() // повторный вызов метода
        }

        return NodePair(nextNode.prev!!, nextNode)
    }

    // получение узла для следующего хода
    // третий этап - соединение майнеров с их самыми дальними точками
    private fun getNextNode3() : NodePair? {
        // маркер метода
        this.methodNum = 3

        // если все майнеры реализованы
        if(unrealisedMines.isEmpty()){
            println("\t\t\t\tall mines are realised")
            println("\t\t\t\tgoing to the stage 4")
            return getNextNode4() // переход к этапу 4
        }

        println("\t\t\t\tgetting a farthest node")
        val mine = unrealisedMines.first() // исходный майнер
        val farthestNode = furthestNodeFrom(mine) // получение самого удаленного узла

        // если майнер изолирован
        if(farthestNode == null) {
            unrealisedMines.add(mine) // удаление из списка нереализованных
            println("\t\t\t\tthis mine is isolated")
            println("\t\t\t\trepeat $methodNum")
            getNextNode3() // повторный вызов метода
        }

        // определение следующего узла на пути к самому удаленному узлу
        val nextNode = nextPathNode(mine, farthestNode!!)

        // если путь развернулся до исходного майнера,
        // значит майнер уже реализован
        if(nextNode == farthestNode) {
            unrealisedMines.remove(mine) // удаление майнера из списка обособленных
            println("\t\t\t\tthis miner is already realised")
            println("\t\t\t\trepeat $methodNum")
            getNextNode3() // повторный вызов метода
        }

        return NodePair(nextNode.prev!!, nextNode)
    }

    // получение узла для следующего хода
    // четвертый этап - захват всех оставшихся узлов
    private fun getNextNode4() : NodePair? {
        // маркер метода
        this.methodNum = 3

        // поиск свободного узла с захваченным соседом
        // и возврат их пары для хода
        println("\t\t\t\tgetting any free node to move")
        for(node in nodes.values)
            if(node.isNeutral())
                for(neighbour in node.links)
                    if(neighbour.isTwiners())
                        return NodePair(neighbour, node)

        // если на последнем этапе не найдено ходов
        println("\t\t\t\tthere are no more neutral nodes")
        return null
    }

    // метод будет дописан после выявления особенностей ботов соперников
    fun getTrick() : NodePair? = null
}