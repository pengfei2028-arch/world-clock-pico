package com.wupengfei.worldclock.content

import java.io.File
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Audit the generated geometry, rather than just repeating the label's input values. */
class CoordinateGeographicAuditTest {
    private val surface by lazy{CoordinateSurface.read(File("src/main/assets/coordinates/earth_surface.bin").inputStream())}
    private fun center(mesh:CoordinateGeometry,offset:Int)=mesh.positions.drop(offset).take(8)
        .fold(GlobeVector3.ZERO){a,b->a+b}*(1.0/8)

    @Test fun generatedTicksReadTheirGeographicDegreesAndCorrectHemisphere(){
        val mesh=CoordinateGeometry.rulers(surface)
        val rows=mutableListOf("axis,tick_degrees,latitude,longitude")
        var offset=0
        var maxLatitude=0.0;var maxLongitude=0.0
        for(latitude in listOf(true,false)){
            // The mesh contains two 71-ring latitude arcs or one 151-ring longitude arc,
            // followed by the capped radial tick tubes (two 8-vertex rings + 2 caps).
            offset+=if(latitude)2*(71*8+2) else 151*8+2
            for(degree in -75..75 step 5){
                if(latitude&&degree==0)continue
                val a=center(mesh,offset);val b=center(mesh,offset+8)
                assertTrue("tick must point radially away from the globe",a.normalized().dot(b.normalized())>.999999999)
                val actual=checkNotNull(surface.coordinate(a))
                val expectedLat=if(latitude)degree.toDouble() else 0.0
                val expectedLon=if(latitude)0.0 else degree.toDouble()
                val latError=abs(actual.latitude-expectedLat)
                val lonError=abs(SolarPosition.wrap(actual.longitude!!-expectedLon))
                maxLatitude=max(maxLatitude,latError);maxLongitude=max(maxLongitude,lonError)
                assertEquals("tick latitude $latitude $degree",expectedLat,actual.latitude,.001)
                assertEquals("tick longitude $latitude $degree",expectedLon,actual.longitude!!,.001)
                rows.add("${if(latitude)"latitude" else "longitude"},$degree,${actual.latitude},${actual.longitude}")
                offset+=18
            }
        }
        assertEquals(mesh.positions.size,offset)
        val output=File("build/reports/coordinate-geographic-audit.csv")
        output.parentFile?.mkdirs();output.writeText(rows.joinToString("\n")+"\n")
        println("Actual ruler tick errors: latitude=$maxLatitude, longitude=$maxLongitude degrees")
    }

    @Test fun coloredReferencesAreZeroAxesAcrossTheWholeNonPolarGlobe(){
        val equator=CoordinateGeometry.reference(surface,true)
        val prime=CoordinateGeometry.reference(surface,false)
        for(degree in -174..174){
            val c=checkNotNull(surface.coordinate(center(equator,(degree+180)*8)))
            assertEquals(0.0,c.latitude,.001)
            assertEquals(degree.toDouble(),c.longitude!!,.001)
        }
        for(degree in -70..70){
            val c=checkNotNull(surface.coordinate(center(prime,(degree+90)*8)))
            assertEquals(degree.toDouble(),c.latitude,.001)
            assertEquals("must never color the 180-degree antimeridian as prime",0.0,c.longitude!!,.001)
        }
    }
}
