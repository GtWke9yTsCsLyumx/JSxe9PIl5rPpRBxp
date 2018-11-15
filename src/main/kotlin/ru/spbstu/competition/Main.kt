package ru.spbstu.competition

import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import ru.spbstu.competition.game.Graph
import ru.spbstu.competition.protocol.Protocol
import ru.spbstu.competition.protocol.data.*

object Arguments {
    @Option(name = "-u", usage = "Specify server url")
    var url: String = "kotoed.icc.spbstu.ru"

    @Option(name = "-p", usage = "Specify server port")
    var port: Int = 50001

    fun use(args: Array<String>): Arguments =
            CmdLineParser(this).parseArgument(*args).let{ this }
}

fun main(args: Array<String>) {
    Arguments.use(args)

    println("Couple of seeds...")

    val protocol = Protocol(Arguments.url, Arguments.port)
    println("\t- protocol")

    protocol.handShake("a nu ka sigraem blin!") //I wanna grow here
    println("\t- handshake")
    val setupData = protocol.setup()
    val graph = Graph(setupData)

    println("Twiner is planted. (id: ${setupData.punter})\n\n")

    protocol.ready()

    var moveNum = 0
    gameloop@ while(true) {
        moveNum++
        val message = protocol.serverMessage()
        when(message) {
            is GameResult -> {
                println("Twiner has grown.")
                val myScore = message.stop.scores[protocol.myId]
                println("points: ${myScore.score}")
                break@gameloop
            }
            is Timeout -> {
                println("Twiner growing too slow :(")
            }
            is GameTurnMessage -> {
                for(move in message.move.moves)
                    if(move is ClaimMove) graph.update(move.claim)
            }
        }

        val targetNode = graph.getNextNode()
        if(targetNode == null) {
            protocol.passMove()
            println("pass")
        }
        else {
            println("$moveNum) ${graph.getCurrentNode().id} -> ${targetNode.id} (${graph.getMethodNum()})" +
                    " isNeighbour=${graph.getCurrentNode().links.contains(targetNode)} state=${targetNode.state}")
            protocol.claimMove(graph.getCurrentNode().id, targetNode.id)
        }
    }
}
