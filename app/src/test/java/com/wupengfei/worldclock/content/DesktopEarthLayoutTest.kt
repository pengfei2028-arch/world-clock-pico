package com.wupengfei.worldclock.content

import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopEarthLayoutTest {
    @Test fun maximumSphereAndControlRegionFitWithoutIntersection() {
        val layout=DesktopEarthLayout
        val radius=layout.MAX_DIAMETER/2
        // A sphere's bounds are unchanged by arbitrary user rotation.
        assertTrue(radius<layout.WIDTH/2)
        assertTrue(radius<layout.DEPTH/2)
        assertTrue(layout.CENTER_Y+radius<layout.HEIGHT/2)
        val globeBottom=layout.CENTER_Y-radius
        val controlsTop=-layout.HEIGHT/2+layout.CONTROLS_BOTTOM_PADDING+layout.CONTROLS_HEIGHT
        assertTrue(globeBottom-controlsTop>=.05f)
        assertTrue(layout.CONTROLS_Z>radius)
        assertTrue(layout.CONTROLS_Z<layout.DEPTH/2)
    }
}
