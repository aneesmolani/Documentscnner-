package com.example.documentscanner.vision

import org.opencv.core.Point
import kotlin.math.hypot
import kotlin.math.max

data class Quad(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point
) {
    fun scaled(factor: Double): Quad = Quad(
        Point(topLeft.x * factor, topLeft.y * factor),
        Point(topRight.x * factor, topRight.y * factor),
        Point(bottomRight.x * factor, bottomRight.y * factor),
        Point(bottomLeft.x * factor, bottomLeft.y * factor)
    )
}

fun Quad.averageEdgeLength(): Double {
    val w1 = hypot(topRight.x - topLeft.x, topRight.y - topLeft.y)
    val w2 = hypot(bottomRight.x - bottomLeft.x, bottomRight.y - bottomLeft.y)
    val h1 = hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y)
    val h2 = hypot(bottomRight.x - topRight.x, bottomRight.y - topRight.y)
    return (w1 + w2 + h1 + h2) / 4.0
}

fun quadDistance(a: Quad, b: Quad, width: Double, height: Double): Double {
    val scale = max(1.0, max(width, height))
    val pointsA = listOf(a.topLeft, a.topRight, a.bottomRight, a.bottomLeft)
    val pointsB = listOf(b.topLeft, b.topRight, b.bottomRight, b.bottomLeft)
    return pointsA.zip(pointsB).map { (p, q) ->
        hypot(p.x - q.x, p.y - q.y) / scale
    }.average()
}
