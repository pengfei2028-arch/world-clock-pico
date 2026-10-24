package com.wupengfei.worldclock.content

import java.io.File
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class CoordinateRulerTest {
    private val surface by lazy{CoordinateSurface.read(File("src/main/assets/coordinates/earth_surface.bin").inputStream())}
    private fun length(p:GlobeVector3)=sqrt(p.dot(p))
    @Test fun fixedLabelsReadTheirActualAuthoredUvAndPointToTheSameRadialLine(){
        for(slot in CoordinateRuler.slots){
            val point=slot.surfaceLocal(surface);val label=slot.labelLocal(surface)
            assertEquals(1.0,point.normalized().dot(label.normalized()),1e-10)
            assertEquals(2*CoordinateStyle.RULER_LABEL_RADIUS,length(label),1e-10)
            val read=checkNotNull(surface.coordinate(point))
            assertEquals(slot.coordinate.latitude,read.latitude,.0002)
            assertEquals(slot.coordinate.longitude!!,read.longitude!!,.0002)
        }
    }
    @Test fun gridTicksAndLabelsMoveTogetherUnderNestedSpinTiltAndScale(){
        val center=GlobeVector3(2.0,-1.0,-60.0)
        for(scale in listOf(.125,.2,12.0))for(degrees in listOf(-170.0,-60.0,0.0,53.0,170.0)){
            val q=GlobeQuaternion.axisAngle(GlobeVector3.Y,41.0)*GlobeQuaternion.axisAngle(GlobeVector3.X,-90.0)*GlobeQuaternion.axisAngle(GlobeVector3.Y,23.44)*GlobeQuaternion.axisAngle(GlobeVector3.Z,degrees)
            for(slot in CoordinateRuler.slots){
                val p=slot.surfaceLocal(surface);val label=slot.labelLocal(surface)
                val globe=center+q.rotate(p)*scale
                val tick=center+q.rotate(p*CoordinateStyle.RULER_RADIUS)*scale
                val text=center+q.rotate(label)*scale
                assertEquals(1.0,(tick-globe).normalized().dot((text-globe).normalized()),1e-8)
                assertTrue("label must move with globe, not stay camera-fixed",length(text-(center+label*scale))>.01*scale)
                val local=q.inverse().rotate((text-center)*(1/scale))*(1/CoordinateStyle.RULER_LABEL_RADIUS)
                val read=checkNotNull(surface.coordinate(local))
                assertEquals(slot.coordinate.latitude,read.latitude,.0002)
                assertEquals(slot.coordinate.longitude!!,read.longitude!!,.0002)
            }
        }
    }
    @Test fun namedReferencesAndHemisphereLabelsAreUnambiguous(){
        assertEquals("赤道",CoordinateRuler.slots[2].title)
        assertEquals("本初子午线",CoordinateRuler.slots[7].title)
        assertEquals("0°",CoordinateRuler.slots[2].text)
        assertEquals("0°",CoordinateRuler.slots[7].text)
        assertEquals("60° S",CoordinateRuler.slots[0].text)
        assertEquals("60° W",CoordinateRuler.slots[5].text)
        assertEquals("60° E",CoordinateRuler.slots[9].text)
        assertEquals(setOf(2,7),CoordinateRuler.displayOrder.take(2).toSet())
        assertNotEquals(CoordinateRuler.slots[2].rgb,CoordinateRuler.slots[7].rgb)
    }
    @Test fun separateColoredMeshesAreEquatorAndOnlyZeroLongitudeHalfMeridian(){
        val equator=CoordinateGeometry.reference(surface,true)
        val prime=CoordinateGeometry.reference(surface,false)
        fun sectionCenter(mesh:CoordinateGeometry,index:Int)=mesh.positions.drop(index*8).take(8).fold(GlobeVector3.ZERO){a,b->a+b}*(1.0/8)
        for(degree in -60..60 step 30){
            val eq=checkNotNull(surface.coordinate(sectionCenter(equator,degree+180)))
            val pm=checkNotNull(surface.coordinate(sectionCenter(prime,degree+90)))
            assertEquals(0.0,eq.latitude,.001);assertEquals(degree.toDouble(),eq.longitude!!,.001)
            assertEquals(degree.toDouble(),pm.latitude,.001);assertEquals(0.0,pm.longitude!!,.001)
        }
        val equatorDiameter=length(equator.positions[0]-equator.positions[4])
        val primeDiameter=length(prime.positions[0]-prime.positions[4])
        assertTrue(equatorDiameter>primeDiameter&&primeDiameter>CoordinateStyle.GRID_WIDTH*1.5)
    }
    @Test fun longitudeScaleIsOnEquatorAndLatitudeScaleIsOnPrimeMeridian(){
        for(slot in CoordinateRuler.slots.filterNot{it.reference}){
            val actual=checkNotNull(surface.coordinate(slot.surfaceLocal(surface)))
            if(slot.latitude){assertEquals(0.0,actual.longitude!!,.001);assertEquals(slot.degrees.toDouble(),actual.latitude,.001)}
            else{assertEquals(0.0,actual.latitude,.001);assertEquals(slot.degrees.toDouble(),actual.longitude!!,.001)}
        }
    }
    @Test fun labelRotationOnlyChangesStageZUnderEveryParentTiltAndSpin(){
        for(scale in listOf(.125,12.0,20.0))for(pitch in -180..180 step 30)for(yaw in -180..180 step 30){
            val parent=GlobeQuaternion.axisAngle(GlobeVector3.Z,23.44)*
                GlobeQuaternion.axisAngle(GlobeVector3.X,pitch.toDouble())*
                GlobeQuaternion.axisAngle(GlobeVector3.Y,yaw.toDouble())
            val measured=CoordinateRuler.rotationFromBasis(parent.rotate(GlobeVector3.X)*scale,
                parent.rotate(GlobeVector3.Y)*scale,parent.rotate(GlobeVector3.Z)*scale)
            for(roll in listOf(-60.0,-45.0,0.0,45.0,60.0)){
                val local=CoordinateRuler.localTextRotation(measured,roll)
                val world=(parent*local).normalized()
                assertEquals(1.0,world.rotate(GlobeVector3.Z).dot(GlobeVector3.Z),1e-9)
                assertTrue("Z normal alone does not detect upside-down text",world.rotate(GlobeVector3.Y).dot(GlobeVector3.Y)>=.5-1e-9)
                val expected=GlobeQuaternion.axisAngle(GlobeVector3.Z,roll).rotate(GlobeVector3.X)
                assertTrue(length(world.rotate(GlobeVector3.X)-expected)<1e-9)
            }
        }
    }
    @Test fun repeatedForwardAndReverseTurnsNeverInvertOrSnapText(){
        for(latitude in listOf(true,false)){
            var previous=0.0
            val degrees=(-1080..1080 step 2).toList()+(1080 downTo -1080 step 2).toList()
            for(degree in degrees){
                val tangent=GlobeQuaternion.axisAngle(GlobeVector3.Z,degree.toDouble())
                    .rotate(if(latitude)GlobeVector3.Y else GlobeVector3.X)
                val angle=CoordinateRuler.rollDegrees(tangent,latitude,previous,1/90.0)
                assertTrue(abs(angle)<=CoordinateStyle.LABEL_ROLL_MAX_DEGREES)
                assertTrue("text up must remain above horizontal",cos(Math.toRadians(angle))>=.5)
                assertTrue("frame rotation must be bounded",abs(angle-previous)<=CoordinateStyle.LABEL_ROLL_MAX_SPEED/90+1e-8)
                previous=angle
            }
        }
    }
    @Test fun reversingTheGeographicTangentNeverFlipsTheReadingDirection(){
        for(latitude in listOf(true,false))for(degree in -180..180){
            val tangent=GlobeQuaternion.axisAngle(GlobeVector3.Z,degree.toDouble()).rotate(GlobeVector3.Y)
            val target=CoordinateRuler.uprightRollTarget(tangent,latitude)
            assertEquals(target,CoordinateRuler.uprightRollTarget(tangent*(-1.0),latitude),1e-9)
        }
        // V69 gave +/-180 here while its Z-normal check still passed.
        assertEquals(0.0,CoordinateRuler.uprightRollTarget(GlobeVector3.Y*(-1.0),true),1e-9)
        assertEquals(0.0,CoordinateRuler.uprightRollTarget(GlobeVector3.X*(-1.0),false),1e-9)
    }
    @Test fun verticalAxisCrossingsHaveContinuousTargetsInsteadOfHalfTurnWraps(){
        for(latitude in listOf(true,false)){
            var previous:Double?=null
            for(step in -1800..1800){
                val tangent=GlobeQuaternion.axisAngle(GlobeVector3.Z,step*.1)
                    .rotate(if(latitude)GlobeVector3.Y else GlobeVector3.X)
                val target=CoordinateRuler.uprightRollTarget(tangent,latitude)
                previous?.let{assertTrue("vertical crossing jumps by ${target-it}",abs(target-it)<.5)}
                assertTrue(abs(target)<=60.0);previous=target
            }
        }
    }
    @Test fun edgeOnProjectionNoiseAndInvalidDirectionsSettleSafely(){
        for(latitude in listOf(true,false))for(i in -100..100){
            val noise=GlobeVector3(i*.00001,cos(i.toDouble())*.0003,if(i%2==0)1.0 else -1.0)
            assertEquals(0.0,CoordinateRuler.uprightRollTarget(noise,latitude),0.0)
        }
        for(bad in listOf(GlobeVector3.ZERO,GlobeVector3(Double.NaN,0.0,1.0))){
            var angle=50.0
            repeat(120){angle=CoordinateRuler.rollDegrees(bad,true,angle,1/60.0)}
            assertTrue(angle.isFinite()&&abs(angle)<.001)
        }
    }
    @Test fun rapidReversalsAndFrameGapsCannotCauseAFlip(){
        var angle=0.0
        repeat(500){i->
            val previous=angle;val dt=listOf(1/120.0,1/30.0,0.0,1.0)[i%4]
            val tangent=GlobeQuaternion.axisAngle(GlobeVector3.Z,if(i%2==0)55.0 else -55.0).rotate(GlobeVector3.Y)
            angle=CoordinateRuler.rollDegrees(tangent,true,angle,dt)
            assertTrue(abs(angle-previous)<=CoordinateStyle.LABEL_ROLL_MAX_SPEED*dt.coerceAtMost(.1)+1e-9)
            assertTrue(abs(angle)<=60.0)
            if(dt==0.0)assertEquals(previous,angle,0.0)
        }
    }
    @Test fun namedReferencesHaveDistinctFixedAnchorsOnTheirActualZeroLines(){
        val equator=CoordinateRuler.slots[2].surfaceLocal(surface)
        val prime=CoordinateRuler.slots[7].surfaceLocal(surface)
        assertTrue(length(equator-prime)>1.0)
        assertEquals(0.0,checkNotNull(surface.coordinate(equator)).latitude,.001)
        assertEquals(0.0,checkNotNull(surface.coordinate(prime)).longitude!!,.001)
    }
    @Test fun datelineGridDoesNotHaveTheOldSawtoothOrAnOpenSeam(){
        fun turn(a:GlobeVector3,b:GlobeVector3,c:GlobeVector3):Double =
            Math.toDegrees(acos((b-a).normalized().dot((c-b).normalized()).coerceIn(-1.0,1.0)))
        val dateline=surface.longitudeLine(-180.0)
        for(i in 1 until dateline.lastIndex){
            val angle=turn(dateline[i-1],dateline[i],dateline[i+1])
            assertTrue("dateline latitude ${i-90} bend $angle",angle<8.0)
        }
        for(lat in -60..60 step 30){
            val path=surface.latitudeLine(lat.toDouble())
            assertEquals(0.0,length(path.first()-path.last()),1e-10)
            val seamTurn=turn(path[path.lastIndex-1],path.first(),path[1])
            assertTrue("closed latitude seam $lat turn=$seamTurn",seamTurn<3.0)
        }
    }
}
