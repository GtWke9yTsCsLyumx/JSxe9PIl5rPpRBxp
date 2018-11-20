package ru.spbstu.competition

import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import ru.spbstu.competition.game.Graph
import ru.spbstu.competition.game.NodeStates
import ru.spbstu.competition.protocol.Protocol
import ru.spbstu.competition.protocol.data.*

object Arguments {
    @Option(name = "-u", usage = "Specify server url")
    var url: String = "kotoed.icc.spbstu.ru"

    @Option(name = "-p", usage = "Specify server port")
    var port: Int = 50005

    fun use(args: Array<String>): Arguments =
            CmdLineParser(this).parseArgument(*args).let{ this }
}

fun main(args: Array<String>) {
    Arguments.use(args)

    println("Couple of seeds...")

    val protocol = Protocol(Arguments.url, Arguments.port)

    protocol.handShake("botbotbotbot") //I wanna grow here
    println("\twaiting for players")
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
                println("\ttwiner has grown")
                val myScore = message.stop.scores[protocol.myId]
                println("\tpoints: ${myScore.score}")
                break@gameloop
            }
            is Timeout -> {
                println("\ttwiner growing too slow :(")
            }
            is GameTurnMessage -> {
                for(move in message.move.moves)
                    if(move is ClaimMove) graph.update(move.claim)
            }
        }

        println("MOVE $moveNum")
        var movePair = graph.getTrick() // попытка насолить басурманам
        if(movePair == null) // если все-таки придется сыграть честно
            movePair = graph.getNextNode() // непорочное получение след. хода

        // если ход не найден
        if(movePair == null) {
            protocol.passMove()
            println("\ttwiner can't find a way to grow")
        }
        else {
            println("\ttry: ${movePair.source.id} -> ${movePair.target.id} (${graph.getMethodNum()})" +
                    " (neighbour=${movePair.source.links.contains(movePair.target)}" +
                    " neutral=${movePair.target.state == NodeStates.NEUTRAL} miner=${graph.nodeIsMiner(movePair.target)})")
            protocol.claimMove(movePair.source.id, movePair.target.id)
            graph.saveLastMove(movePair)
        }
    }
}
