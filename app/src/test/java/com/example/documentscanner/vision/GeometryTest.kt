package com.example.documentscanner.vision

import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point

class GeometryTest {
    @Test fun identicalQuadsHaveZeroDistance() {
        val q = Quad(Point(0.0,0.0),Point(100.0,0.0),Point(100.0,100.0),Point(0.0,100.0))
        assertTrue(quadDistance(q,q,100.0,100.0) == 0.0)
    }

    @Test fun averageEdgeLengthIsPositive() {
        val q = Quad(Point(0.0,0.0),Point(100.0,0.0),Point(100.0,200.0),Point(0.0,200.0))
        assertTrue(q.averageEdgeLength() > 0.0)
    }
}
