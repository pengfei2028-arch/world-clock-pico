package com.wupengfei.worldclock.content

/** Reserve the full distance capsule, including its unit. Only city cards yield;
 * the geographic endpoints, arc and midpoint readout never move for layout. */
internal class DistanceLabelLayout {
    private val avoidance=CityLabelAvoidance()
    fun update(cities:List<CityLabelCandidate>,readout:GlobeVector3?,unit:Double,
        lower:Double,upper:Double,seconds:Double):Map<String,CityLabelLayout>{
        val reserved=readout?.let{CityLabelCandidate(READOUT,CityLabelLayout(it,it,true,
            DistanceStyle.PANEL_WIDTH*unit/2,DistanceStyle.PANEL_HEIGHT*unit/2))}
        val arranged=avoidance.update(cities+listOfNotNull(reserved),unit,lower,upper,seconds)
        // Recenter the packed layout around the fixed midpoint. A common translation
        // preserves every non-overlap constraint, including during smooth transitions.
        val shift=if(readout==null)0.0 else readout.y-checkNotNull(arranged[READOUT]).center.y
        return arranged.filterKeys{it!=READOUT}.mapValues{it.value.atY(it.value.center.y+shift)}
    }
    companion object {private const val READOUT="distance-midpoint-reserved"}
}
