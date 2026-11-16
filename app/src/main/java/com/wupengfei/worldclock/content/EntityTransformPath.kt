package com.wupengfei.worldclock.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent

/** Explicit local TRS composition. Reads the same cached transforms written by scene animation,
 * rather than mixing Entity.convertPositionTo results with uncommitted batched transforms. */
internal class EntityTransformPath(source:Entity,target:Entity?) {
    private fun ancestors(start:Entity?):List<Entity> {
        val result=mutableListOf<Entity>();var current=start
        while(current!=null){check(current !in result){"Cyclic scene hierarchy"};result.add(current);current=current.getParent()}
        return result
    }
    private val from:List<TransformComponent>
    private val to:List<TransformComponent>
    init {
        val a=ancestors(source);val b=ancestors(target)
        val common=a.firstOrNull{it in b}
        from=a.takeWhile{it!=common}.mapNotNull{it.components[TransformComponent::class.java]}
        to=b.takeWhile{it!=common}.mapNotNull{it.components[TransformComponent::class.java]}
    }
    private fun compose(path:List<TransformComponent>):QuakeSurfaceFrame {
        var frame=QuakeSurfaceFrame.IDENTITY
        for(transform in path){
            val q=transform.quaternion
            frame=frame.then(QuakeSurfaceFrame.trs(CoordinateRenderer.vec(transform.position),
                GlobeQuaternion(q.x.toDouble(),q.y.toDouble(),q.z.toDouble(),q.w.toDouble()).normalized(),
                CoordinateRenderer.vec(transform.scaleVector)))
        }
        return frame
    }
    fun frame()=compose(from).then(compose(to).inverse())
}
