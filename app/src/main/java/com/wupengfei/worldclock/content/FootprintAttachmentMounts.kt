package com.wupengfei.worldclock.content

/** A new PICO attachment must enter an enabled scene root. SDK 6.1.9's legacy
 * delegate does not mark onAddToTree(enabled=false) as attached, and a later
 * activation alone cannot recover it. Pending mounts therefore remain pending
 * throughout system-picker pauses and failed adds. No extra panels are allocated. */
internal class FootprintAttachmentMounts<T>(
    private val attach:(T)->Boolean,
    private val enable:(T,Boolean)->Unit,
) {
    private val mounted=linkedSetOf<T>()
    val size get()=mounted.size
    fun show(entity:T?,visible:Boolean,sceneActive:Boolean) {
        if(entity==null)return
        if(!sceneActive||!visible){
            if(entity in mounted)enable(entity,false)
            return
        }
        enable(entity,true)
        if(entity !in mounted&&attach(entity))mounted.add(entity)
    }
    fun close(){mounted.forEach{enable(it,false)};mounted.clear()}
}
