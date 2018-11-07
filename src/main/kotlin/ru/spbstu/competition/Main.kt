package ru.spbstu.competition

import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import ru.spbstu.competition.game.Graph
import ru.spbstu.competition.protocol.Protocol
import ru.spbstu.competition.protocol.data.*

object Arguments {
    @Option(name = "-u", usage = "Specify server url")
    var url: String = ""

    @Option(name = "-p", usage = "Specify server port")
    var port: Int = -1

    fun use(args: Array<String>): Arguments =
            CmdLineParser(this).parseArgument(*args).let{ this }
}

fun main(args: Array<String>) {
    Arguments.use(args)

    println("Couple of seeds...")

    val protocol = Protocol(Arguments.url, Arguments.port)

    protocol.handShake("I wanna grow here")
    val setupData = protocol.setup()
    val graph = Graph(setupData)

    println("Twiner is planted. (id: ${setupData.punter})")

    protocol.ready()

    gameloop@ while(true) {
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
            println("Twiner has slowed its growth")
        }
        else {
            protocol.claimMove(graph.getCurrentNode().id, targetNode.id)
            println("Twiner become bigger")
        }
    }
}
