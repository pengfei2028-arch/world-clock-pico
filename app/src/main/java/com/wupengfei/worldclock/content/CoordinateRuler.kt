package com.wupengfei.worldclock.content

import kotlin.math.*

/** Fixed local anchors. No camera projection, foreground plane or label avoidance. */
internal object CoordinateRuler {
    data class Slot(val latitude:Boolean,val degrees:Int) {
        val coordinate get()=when {
            // Named zero references sit at different ends of their own true geographic lines.
            // This is a fixed authored placement, never a viewer-dependent displacement.
            reference&&latitude->GeoCoordinate(0.0,-75.0)
            reference->GeoCoordinate(-75.0,0.0)
            latitude->GeoCoordinate(degrees.toDouble(),CoordinateStyle.LATITUDE_RULER_LONGITUDE)
            else->GeoCoordinate(CoordinateStyle.LONGITUDE_RULER_LATITUDE,degrees.toDouble())
        }
        val reference get()=degrees==0
        val title get()=if(reference){if(latitude)"赤道" else "本初子午线"}else ""
        val text get()=if(reference)"0°" else "${abs(degrees)}° "+if(latitude){if(degrees<0)"S" else "N"}else{if(degrees<0)"W" else "E"}
        val rgb get()=if(reference){if(latitude)CoordinateStyle.EQUATOR_RGB else CoordinateStyle.PRIME_RGB}else 0xFFFFFF
        val widthDp get()=if(reference)180 else 106
        val heightDp get()=if(reference)64 else 40
        fun surfaceLocal(mapping:CoordinateSurface)=mapping.point(coordinate.latitude,coordinate.longitude!!).normalized()*2.0
        fun labelLocal(mapping:CoordinateSurface)=surfaceLocal(mapping)*CoordinateStyle.RULER_LABEL_RADIUS
        fun tangentLocal(mapping:CoordinateSurface):GlobeVector3 {
            val p=coordinate;val lon=checkNotNull(p.longitude)
            return if(latitude)(mapping.point(p.latitude+.2,lon)-mapping.point(p.latitude-.2,lon)).normalized()
                else (mapping.point(p.latitude,lon+.2)-mapping.point(p.latitude,lon-.2)).normalized()
        }
    }
    val slots=listOf(-60,-30,0,30,60).map{Slot(true,it)}+listOf(-60,-30,0,30,60).map{Slot(false,it)}
    // Stable mounting order only; there is no layout priority or avoidance.
    val displayOrder=slots.indices.sortedBy{if(slots[it].reference)0 else 1}
    data class Placement(val position:GlobeVector3,val surface:GlobeVector3,val normal:GlobeVector3)

    /** Reconstruct native parent orientation from transformed basis vectors.
     * Using the actual basis avoids ambiguity about SDK rotation conversion spaces.
     */
    fun rotationFromBasis(x:GlobeVector3,y:GlobeVector3,z:GlobeVector3):GlobeQuaternion {
        val a=x.normalized();val b=y.normalized();val c=z.normalized()
        val trace=a.x+b.y+c.z
        val q=when {
            trace>0->{val s=sqrt(trace+1)*2;GlobeQuaternion((b.z-c.y)/s,(c.x-a.z)/s,(a.y-b.x)/s,s/4)}
            a.x>b.y&&a.x>c.z->{val s=sqrt(1+a.x-b.y-c.z)*2;GlobeQuaternion(s/4,(b.x+a.y)/s,(c.x+a.z)/s,(b.z-c.y)/s)}
            b.y>c.z->{val s=sqrt(1+b.y-a.x-c.z)*2;GlobeQuaternion((b.x+a.y)/s,s/4,(c.y+b.z)/s,(c.x-a.z)/s)}
            else->{val s=sqrt(1+c.z-a.x-b.y)*2;GlobeQuaternion((c.x+a.z)/s,(c.y+b.z)/s,s/4,(a.y-b.x)/s)}
        }
        return q.normalized()
    }
    private fun smoothStep(low:Double,high:Double,value:Double):Double {
        val t=((value-low)/(high-low)).coerceIn(0.0,1.0)
        return t*t*(3-2*t)
    }
    /** A ruler is an undirected line, not an arrow: reversing its tangent must not
     * turn the text upside down. Double-angle representation removes that ambiguity.
     * A hard +/-90 degree wrap would still jump by 180 degrees, so gradually level
     * the glyph near vertical. At an edge-on tangent its direction is undefined;
     * fade the roll toward horizontal instead of amplifying projection noise.
     */
    fun uprightRollTarget(tangent:GlobeVector3,latitude:Boolean):Double {
        val n=tangent.normalized()
        val projected=hypot(n.x,n.y)
        if(projected<1e-8)return 0.0
        val x=(if(latitude)n.y else n.x)/projected
        val y=(if(latitude)-n.x else n.y)/projected
        val axisAngle=Math.toDegrees(atan2(2*x*y,x*x-y*y))*.5
        val nearVertical=1-smoothStep(CoordinateStyle.LABEL_ROLL_FOLLOW_DEGREES,
            CoordinateStyle.LABEL_ROLL_LEVEL_DEGREES,abs(axisAngle))
        val directionConfidence=smoothStep(.08,.25,projected)
        return (axisAngle*nearVertical*directionConfidence)
            .coerceIn(-CoordinateStyle.LABEL_ROLL_MAX_DEGREES,CoordinateStyle.LABEL_ROLL_MAX_DEGREES)
    }
    /** Only bounded Z rotation is filtered. Local label positions are never changed. */
    fun rollDegrees(tangent:GlobeVector3,latitude:Boolean,previous:Double,seconds:Double):Double {
        val target=uprightRollTarget(tangent,latitude)
        val current=if(previous.isFinite())previous.coerceIn(-CoordinateStyle.LABEL_ROLL_MAX_DEGREES,
            CoordinateStyle.LABEL_ROLL_MAX_DEGREES) else 0.0
        val dt=if(seconds.isFinite())seconds.coerceIn(0.0,.1) else 0.0
        val delta=(target-current)*(1-exp(-dt/CoordinateStyle.LABEL_ROLL_RESPONSE_SECONDS))
        val limit=CoordinateStyle.LABEL_ROLL_MAX_SPEED*dt
        return current+delta.coerceIn(-limit,limit)
    }
    fun localTextRotation(parent:GlobeQuaternion,rollDegrees:Double)=
        (parent.inverse()*GlobeQuaternion.axisAngle(GlobeVector3.Z,rollDegrees)).normalized()
}
