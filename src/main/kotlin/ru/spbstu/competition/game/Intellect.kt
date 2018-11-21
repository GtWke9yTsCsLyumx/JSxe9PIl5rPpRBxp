package ru.spbstu.competition.game

import ru.spbstu.competition.protocol.Protocol
import ru.spbstu.competition.protocol.data.River
import java.security.KeyStore
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.jvm.internal.markers.KMutableMap

public class Intellect(val state: State, val protocol: Protocol) {
    //data
    private val orderList = linkedMapOf<River, RiverState>() // Продуманные заранее ходы на очередь
    private val nodes = linkedMapOf<Int, Node>() //просто Все ноды
    //variables
    private var lastSession = 0 //количество ходов?


    fun analyzeMap() { // Обдумаем первые ходы. Захватим реку каждого майнера!
        val state = this.state
        System.out.println("Инициализация интеллекта...")
        System.out.println(state.mines)
        //я не собираюсь трогать более одной реки у каждого майнера
        //val untouchedMines : MutableList<Int> = state.mines.toMutableList() --------- БЕСПОЛЕЗНАЯ СТРОКА
        for (mine in state.mines) {
            val firstRiver = (state.rivers.entries.find { (river, riverState) ->
                riverState == RiverState.Neutral && (river.source == mine || river.target == mine)
            })
            System.out.println("here")
            if (firstRiver != null) {
                System.out.println("River taken ${firstRiver.key.source} -> ${firstRiver.key.target}")
                orderList.put(firstRiver.key, firstRiver.value)
            }
        }
        System.out.println("Инициализация завершена")
    }//тут важна очередь. Возможно, первым майнером должен быть самый многообещающий...нужен алгоритм


    fun makeMove() {
        // Da best strategy ever!

        // Если orderList не пуст, значит у нас есть план и мы не готовы хвататься за случайную реку!*
        print("\t\t\torderList : [")
        orderList.forEach { print("${it}, ") }
        println(" ]")
        if (!orderList.isEmpty()) {
            val clone = linkedMapOf<River, RiverState>()
            for (river in orderList) clone.put(river.key, river.value)
            for (river in clone) {
                if (state.rivers.get(River(river.key.source, river.key.target)) == RiverState.Neutral) {
                    System.out.println("Захват идёт по плану")
                    System.out.println("${river.key.source} -> ${river.key.target} - from/to miner")
                    orderList.remove(river.key, river.value)
                    return protocol.claimMove(river.key.source, river.key.target)
                } else orderList.remove(river.key, river.value)
            }
        }


        // If there is a free river near a mine, take it!
        val try0 = state.rivers.entries.find { (river, riverState) ->
            riverState == RiverState.Neutral && (river.source in state.mines || river.target in state.mines)
        }
        if (try0 != null) {
            System.out.println("${try0.key.source} -> ${try0.key.target} - from/to miner");
            System.out.println("${try0.key.source in state.mines} -> ${try0.key.target in state.mines}")
            return protocol.claimMove(try0.key.source, try0.key.target)
        }

        // Look at all our pointsees
        val ourSites = state
                .rivers
                .entries
                .filter { it.value == RiverState.Our }
                .flatMap { listOf(it.key.source, it.key.target) }
                .toSet()

        // If there is a river between two our pointsees, take it!
        val try1 = state.rivers.entries.find { (river, riverState) ->
            riverState == RiverState.Neutral && (river.source in ourSites && river.target in ourSites)
        }
        if (try1 != null) {
            System.out.println("${try1.key.source} -> ${try1.key.target} - If there is a river between two our pointsee");
            return protocol.claimMove(try1.key.source, try1.key.target)
        }

        // If there is a river near our pointsee, take it!
        val try2 = state.rivers.entries.find { (river, riverState) ->
            riverState == RiverState.Neutral && (river.source in ourSites || river.target in ourSites)
        }
        if (try2 != null) {
            System.out.println("${try2.key.source} -> ${try2.key.target}");
            //System.out.println ("${try2.key.source in state.mines} -> ${try2.key.target in state.mines}")
            return protocol.claimMove(try2.key.source, try2.key.target)
        }

        // Bah, take anything left
        val try3 = state.rivers.entries.find { (_, riverState) ->
            riverState == RiverState.Neutral
        }
        if (try3 != null) {
            System.out.println("${try3.key.source} -> ${try3.key.target}");
            //System.out.println ("${try3.key.source in state.mines} -> ${try3.key.target in state.mines}")
            return protocol.claimMove(try3.key.source, try3.key.target)
        }

        // (╯°□°)╯ ┻━┻
        protocol.passMove()
    }

    private fun furthestNodeFrom(source: Node): Node? {
        resetDistances() // !!!
        val sessionNum = lastSession + 1
        val queue = LinkedList<Node>()
        var farthestNode = Node(-1)
        farthestNode.distance = Int.MIN_VALUE

        // Дейкстра
        source.distance = 0
        var currentNode = source
        queue.add(currentNode)
        while (queue.isNotEmpty()) {
            currentNode = queue.poll()
            for (neighbour in currentNode.links) {
                if (neighbour.updateInfo(sessionNum,
                                currentNode.distance + 1,
                                currentNode)) {
                    if (neighbour.isNeutral() &&
                            neighbour.distance > farthestNode.distance)
                        farthestNode = neighbour
                    queue.add(neighbour)
                }
            }
        }
        lastSession = sessionNum
        if (farthestNode.id == -1) return null
        return farthestNode
    }

    // очистка вычисленных путей (сброс информации на каждом узле)
    private fun resetDistances() {
        nodes.values.forEach { it.resetInfo() }
    }
}
