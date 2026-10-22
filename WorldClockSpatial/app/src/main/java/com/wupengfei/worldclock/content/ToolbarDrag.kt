package com.wupengfei.worldclock.content

import kotlin.math.*

/** Absolute input in the fixed stage XY plane, never coordinates on the moving panel.
 * 35ms response / 0.35dp noise band are project interaction choices. */
internal class ToolbarDrag {
    var x=0.0; private set
    var y=0.0; private set
    var active=false; private set
    private var inputX=0.0; private var inputY=0.0
    private var anchorX=0.0; private var anchorY=0.0
    private var targetX=0.0; private var targetY=0.0
    private var lastTime=0.0
    private var gain=1.0
    companion object {
        // Remote manipulation: project hand translation onto the latched panel depth.
        fun depthGain(panelDepthMetres:Double,handDepthMetres:Double):Double=
            if(!panelDepthMetres.isFinite()||!handDepthMetres.isFinite())1.0
            else (abs(panelDepthMetres)/abs(handDepthMetres).coerceAtLeast(.25)).coerceIn(1.0,6.0)
    }
    fun begin(px:Double,py:Double,now:Double,depthGain:Double=1.0){
        if(!px.isFinite()||!py.isFinite()||!now.isFinite())return
        gain=if(depthGain.isFinite())depthGain.coerceIn(1.0,6.0) else 1.0
        active=true;inputX=px;inputY=py;anchorX=x;anchorY=y;targetX=x;targetY=y;lastTime=now
    }
    fun sample(px:Double,py:Double,now:Double){
        if(!active||!px.isFinite()||!py.isFinite()||!now.isFinite())return
        val dt=now-lastTime
        if(dt<=0.0)return
        lastTime=now
        if(dt>.2){inputX=px;inputY=py;anchorX=targetX;anchorY=targetY;return}
        val tx=(anchorX+(px-inputX)*gain).coerceIn(-400.0,400.0)
        val ty=(anchorY+(py-inputY)*gain).coerceIn(-240.0,240.0)
        val dx=tx-targetX;val dy=ty-targetY;val distance=hypot(dx,dy)
        if(distance>.35){val fraction=(distance-.35)/distance;targetX+=dx*fraction;targetY+=dy*fraction}
    }
    fun advance(seconds:Double){
        if(!active||!seconds.isFinite())return
        val a=1.0-exp(-seconds.coerceIn(0.0,.1)/.035)
        x+=(targetX-x)*a;y+=(targetY-y)*a
    }
    fun end(){active=false;targetX=x;targetY=y}
}
