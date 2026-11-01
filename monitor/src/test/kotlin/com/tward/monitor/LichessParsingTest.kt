package com.tward.monitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LichessParsingTest {

    private val base = "https://lichess.org"

    @Test
    fun `profile reads the four speeds and ignores the rest`() {
        val body = """
            {"id":"tbot","username":"TBot","title":"BOT","perfs":{
              "bullet":{"games":12,"rating":1650,"rd":80,"prog":-14,"prov":true},
              "blitz":{"games":340,"rating":1822,"rd":50,"prog":31},
              "puzzle":{"games":5,"rating":1500},
              "rapid":{"games":3,"rating":1500,"prov":true}}}
        """
        val ratings = LichessParsing.profile(body).ratings
        assertEquals(listOf("bullet", "blitz", "rapid"), ratings.keys.toList())
        assertEquals(PerfRating(1822, 340, provisional = false, progress = 31), ratings["blitz"])
        assertTrue(ratings.getValue("bullet").provisional)
    }

    @Test
    fun `presence reads online and playing`() {
        val presence = LichessParsing.presence("""[{"id":"tbot","name":"TBot","online":true,"playing":true}]""")
        assertEquals(presence, Presence(online = true, playing = true))
        assertEquals(
            LichessParsing.presence("""[{"id":"tbot","name":"TBot"}]"""),
            Presence(online = false, playing = false)
        )
    }

    @Test
    fun `recent games resolve colour, outcome and opponent from the bot's side`() {
        val ndjson = """
            {"id":"g1","rated":true,"speed":"blitz","status":"mate","winner":"black","lastMoveAt":1000,"players":{"white":{"user":{"name":"Human","id":"human"},"rating":1900,"ratingDiff":-6},"black":{"user":{"name":"TBot","id":"tbot"},"rating":1800,"ratingDiff":7}}}
            {"id":"g2","rated":false,"speed":"rapid","status":"draw","lastMoveAt":2000,"players":{"white":{"user":{"name":"TBot","id":"tbot"},"rating":1800},"black":{"aiLevel":5}}}
            not json
            {"id":"g3","rated":true,"speed":"bullet","status":"aborted","lastMoveAt":3000,"players":{"white":{"user":{"name":"TBot","id":"tbot"}},"black":{"user":{"name":"X","id":"x"}}}}
        """.trimIndent()

        val games = LichessParsing.recentGames(ndjson, "tbot", base)
        assertEquals(3, games.size)

        val (win, draw, aborted) = games
        assertEquals("black", win.colour)
        assertEquals(Outcome.WIN, win.outcome)
        assertEquals("Human", win.opponent)
        assertEquals(1900, win.opponentRating)
        assertEquals(7, win.ratingDiff)
        assertEquals("$base/g1", win.url)

        assertEquals(Outcome.DRAW, draw.outcome)
        assertEquals("Stockfish level 5", draw.opponent)
        assertFalse(draw.rated)

        assertEquals(Outcome.ABORTED, aborted.outcome)
    }

    @Test
    fun `playing parses live games and keeps only standard chess`() {
        val body = """
            {"nowPlaying":[
              {"gameId":"live1","color":"black","fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR","isMyTurn":true,"lastMove":"e2e4",
               "opponent":{"id":"h","username":"Human","rating":2010},"rated":true,"secondsLeft":177,"speed":"blitz","variant":{"key":"standard"}},
              {"gameId":"live2","color":"white","fen":"8/8/8/8/8/8/8/8","isMyTurn":false,"opponent":{"username":"Y"},"variant":{"key":"chess960"}}
            ]}
        """
        val games = LichessParsing.playing(body, base)
        assertEquals(1, games.size)
        val game = games.single()
        assertEquals("black", game.colour)
        assertEquals("Human", game.opponent)
        assertEquals(2010, game.opponentRating)
        assertTrue(game.botToMove)
        assertEquals(177, game.botSecondsLeft)
        assertEquals("e2e4", game.lastMove)
        assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b - - 0 1", game.fen)
    }

    @Test
    fun `normaliseFen keeps full FENs and pads partial ones`() {
        val full = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        assertEquals(full, LichessParsing.normaliseFen(full, "w"))
        assertEquals("8/8/8/8/8/8/8/8 w - - 0 1", LichessParsing.normaliseFen("8/8/8/8/8/8/8/8", "w"))
        assertEquals("8/8/8/8/8/8/8/8 b KQ - 0 1", LichessParsing.normaliseFen("8/8/8/8/8/8/8/8 b KQ", "w"))
    }
}
