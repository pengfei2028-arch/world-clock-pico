package com.wupengfei.worldclock.content

import kotlin.math.*

internal data class GlobeVector3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(v: GlobeVector3) = GlobeVector3(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: GlobeVector3) = GlobeVector3(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Double) = GlobeVector3(x * s, y * s, z * s)
    fun dot(v: GlobeVector3) = x*v.x + y*v.y + z*v.z
    fun cross(v: GlobeVector3) = GlobeVector3(y*v.z-z*v.y, z*v.x-x*v.z, x*v.y-y*v.x)
    fun normalized(): GlobeVector3 {
        val n = sqrt(dot(this))
        return if (n.isFinite() && n > 1e-12) this * (1.0 / n) else ZERO
    }
    companion object {
        val ZERO = GlobeVector3(0.0, 0.0, 0.0)
        val X = GlobeVector3(1.0, 0.0, 0.0)
        val Y = GlobeVector3(0.0, 1.0, 0.0)
        val Z = GlobeVector3(0.0, 0.0, 1.0)
    }
}

internal data class GlobeQuaternion(val x: Double, val y: Double, val z: Double, val w: Double) {
    operator fun times(q: GlobeQuaternion) = GlobeQuaternion(
        w*q.x+x*q.w+y*q.z-z*q.y, w*q.y-x*q.z+y*q.w+z*q.x,
        w*q.z+x*q.y-y*q.x+z*q.w, w*q.w-x*q.x-y*q.y-z*q.z)
    fun normalized(): GlobeQuaternion {
        val n = sqrt(x*x+y*y+z*z+w*w)
        return if (n.isFinite() && n > 1e-12) GlobeQuaternion(x/n,y/n,z/n,w/n) else IDENTITY
    }
    fun inverse() = GlobeQuaternion(-x,-y,-z,w)
    fun rotate(v: GlobeVector3): GlobeVector3 {
        val q = GlobeVector3(x,y,z)
        val t = q.cross(v) * 2.0
        return v + t*w + q.cross(t)
    }
    fun angleTo(q: GlobeQuaternion): Double =
        2.0 * acos(abs(x*q.x+y*q.y+z*q.z+w*q.w).coerceIn(0.0,1.0)) * 180.0 / PI
    companion object {
        val IDENTITY = GlobeQuaternion(0.0,0.0,0.0,1.0)
        fun axisAngle(axis: GlobeVector3, degrees: Double): GlobeQuaternion {
            val n = axis.normalized()
            if (n == GlobeVector3.ZERO || !degrees.isFinite()) return IDENTITY
            val half = degrees * PI / 360.0; val s = sin(half)
            return GlobeQuaternion(n.x*s,n.y*s,n.z*s,cos(half))
        }
        fun align(from: GlobeVector3, to: GlobeVector3): GlobeQuaternion {
            val a = from.normalized(); val b = to.normalized()
            val d = a.dot(b).coerceIn(-1.0,1.0)
            if (d > 1.0 - 1e-12) return IDENTITY
            if (d < -1.0 + 1e-10) {
                val seed = if (abs(a.x) < 0.9) GlobeVector3.X else GlobeVector3.Y
                return axisAngle(a.cross(seed),180.0)
            }
            val c = a.cross(b)
            return GlobeQuaternion(c.x,c.y,c.z,1.0+d).normalized()
        }
        fun slerp(a: GlobeQuaternion, b: GlobeQuaternion, amount: Double): GlobeQuaternion {
            val t = amount.coerceIn(0.0,1.0)
            var d = a.x*b.x+a.y*b.y+a.z*b.z+a.w*b.w
            val q = if (d < 0.0) GlobeQuaternion(-b.x,-b.y,-b.z,-b.w) else b
            d = abs(d).coerceIn(0.0,1.0)
            val theta = acos(d)
            val u = if (d > 0.9995) 1.0-t else sin((1.0-t)*theta)/sin(theta)
            val v = if (d > 0.9995) t else sin(t*theta)/sin(theta)
            return GlobeQuaternion(a.x*u+q.x*v,a.y*u+q.y*v,a.z*u+q.z*v,a.w*u+q.w*v).normalized()
        }
    }
}
