package com.wupengfei.worldclock.content

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.graphics.SpatialHoverStyle
import com.wupengfei.worldclock.R
import androidx.compose.ui.platform.LocalView

@Composable internal fun FootprintToggle(clock:ClockState) {
    GlassAction({clock.toggleFootprints()},Modifier.size(48.dp).semantics{contentDescription="个人足迹";selected=clock.footprints.enabled},
        containerTint=if(clock.footprints.enabled)Color(AppSelectionStyle.COLOR_ARGB).copy(alpha=AppSelectionStyle.CONTAINER_ALPHA)else Color.Transparent){
        Image(painterResource(R.drawable.ic_footprints),null,Modifier.size(24.dp))
    }
}

@Composable private fun LocalPhoto(images:FootprintImages?,photo:FootprintPhoto?,modifier:Modifier,hero:Boolean=false) {
    // key() disposes the old photo immediately; a city switch cannot flash the previous image.
    key(images,photo?.id,hero){
        var bitmap by remember{mutableStateOf<Bitmap?>(null)}
        var done by remember{mutableStateOf(false)}
        LaunchedEffect(images,photo){
            try{if(images!=null&&photo!=null)bitmap=if(hero)images.preview(photo)else images.thumbnail(photo)}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){android.util.Log.w("WorldClockFootprints","Photo preview unreadable",e)}
            finally{done=true}
        }
        Box(modifier,contentAlignment=Alignment.Center){
            bitmap?.let{Image(it.asImageBitmap(),"个人照片",Modifier.fillMaxSize(),contentScale=if(hero)ContentScale.Fit else ContentScale.Crop)}
                ?:Text(if(photo==null)"＋" else if(done)"无法读取" else "…",style=uiText(if(hero)20 else 16),color=Color.White.copy(alpha=.8f))
        }
    }
}

@Composable internal fun FootprintPin(clock:ClockState,slot:Int) {
    val f=clock.footprints
    val city=f.cities(clock.catalog).getOrNull(slot)
    Box(Modifier.size(200.dp,96.dp).alpha(f.pinAlpha[slot])){
        if(f.enabled&&f.pinAlpha[slot]>.001f&&city!=null){
            val chosen=f.selectedCityId==city.id&&f.albumOpen
            val photo=if(chosen)f.photo else f.albums.firstOrNull{it.cityId==city.id}?.photos?.firstOrNull()
            // Only the image clips. Border is drawn INSIDE the padded 68dp photo slot.
            Row(Modifier.align(Alignment.TopStart).height(72.dp)
                .semantics{contentDescription="${city.name}相册";selected=chosen}
                .clickable(interactionSource=remember{MutableInteractionSource()},indication=null){clock.selectFootprint(city)},
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Box(Modifier.size(72.dp),contentAlignment=Alignment.Center){
                    val diameter=if(chosen)64.dp else 52.dp
                    LocalPhoto(f.images,photo,Modifier.size(diameter).clip(CircleShape).background(Color.Black.copy(alpha=.5f))
                        .border(if(chosen)3.dp else 2.dp,Color.White,CircleShape))
                }
                Box(Modifier.height(48.dp).widthIn(max=120.dp).clip(CircleShape).backgroundMaterial().padding(horizontal=14.dp),contentAlignment=Alignment.Center){
                    Text(city.name,style=uiText(18,FontWeight.Medium),maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.offset(35.dp,72.dp).size(2.dp,8.dp).background(Color.White.copy(alpha=.65f)))
            Box(Modifier.offset(32.dp,80.dp).size(8.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable private fun PanelClose(label:String,size:Int=48,onClick:()->Unit) {
    GlassAction(onClick,Modifier.size(size.dp).semantics{contentDescription=label},horizontalPadding=0.dp){
        Image(painterResource(com.pico.spatial.ui.design.R.drawable.ic_sui_close),null,Modifier.size(24.dp))
    }
}

@Composable internal fun FootprintPlaces(clock:ClockState) {
    val f=clock.footprints;val context=LocalContext.current
    var manage by remember(clock.panelEpoch,f.placesSearching){mutableStateOf(false)}
    if(!f.enabled||!f.placesOpen)return
    val available=f.initialized&&!f.busy&&f.pickingCityId==null&&clock.ready
    if(f.placesSearching){
        Glass(Modifier.width(264.dp).clickable(interactionSource=remember{MutableInteractionSource()},indication=null){clock.touchUi()}){
            Row(Modifier.fillMaxWidth().height(32.dp),verticalAlignment=Alignment.CenterVertically){
                Text(if(manage)"管理地点" else "添加地点",style=uiText(16,FontWeight.Medium),modifier=Modifier.weight(1f))
                PanelClose("返回地点列表",32){clock.touchUi();f.closeSearch()}
            }
            if(manage){
                Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    f.cities(clock.catalog).forEach{city->
                        val count=f.albums.first{it.cityId==city.id}.photos.size
                        Row(Modifier.fillMaxWidth().height(40.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(city.name,style=uiText(16),modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                            GlassAction({clock.touchUi();f.removeEmptyCity(city.id)},Modifier.height(32.dp),enabled=available&&count==0){
                                Text(if(count==0)"移除" else "已有照片",style=uiText(13))
                            }
                        }
                    }
                }
                Text("已有照片的地点暂不支持移除",style=uiText(12),color=PicoTheme.colorScheme.labelSecondary)
                GlassAction({clock.touchUi();manage=false},Modifier.fillMaxWidth().height(38.dp)){Text("添加地点",style=uiText(14))}
            }else{
                TextField(value=f.query,onValueChange={clock.touchUi();f.query=it},
                    modifier=Modifier.fillMaxWidth().spatialHoverEffect(SpatialHoverStyle.Highlight),textStyle=uiText(16),
                    placeholder={Text("城市或国家",style=uiText(16))},enabled=clock.catalog!=null&&available,
                    colors=TextFieldDefaults.textFieldColors(textColor=Color.White,backgroundColor=Color.White.copy(alpha=.06f),focusedColor=Color.White.copy(alpha=.12f),placeholderColor=Color.White.copy(alpha=.55f)))
                val results=remember(clock.catalog,f.query){clock.catalog?.search(f.query).orEmpty()}
                key(f.query){
                    Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        if(results.isEmpty())Text(if(clock.catalog==null||!f.initialized)"正在载入" else "没有匹配城市",style=uiText(14))
                        results.forEach{city->
                            GlassAction({clock.touchUi();if(f.albums.any{it.cityId==city.id})clock.selectFootprint(city)else f.add(city)},
                                Modifier.fillMaxWidth().height(40.dp),enabled=available){
                                Text(city.name,style=uiText(15),modifier=Modifier.weight(1f).alignByBaseline(),maxLines=1,overflow=TextOverflow.Ellipsis)
                                Spacer(Modifier.width(8.dp))
                                Text(city.country,style=uiText(12),color=PicoTheme.colorScheme.labelSecondary,
                                    modifier=Modifier.widthIn(max=76.dp).alignByBaseline(),maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if(f.albums.isNotEmpty())GlassAction({clock.touchUi();manage=true},Modifier.fillMaxWidth().height(34.dp)){Text("管理地点",style=uiText(13))}
            }
            if(f.error.isNotBlank()&&f.errorCityId==null)Text(f.error,style=uiText(12),color=PicoTheme.colorScheme.labelSecondary)
            Text("地名 © GeoNames · CC BY 4.0",style=uiText(10),color=PicoTheme.colorScheme.labelTertiary)
        }
        return
    }
    // Same 208dp list, 64dp capsules and 40dp bottom plus as World Time.
    Column(Modifier.width(208.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            f.cities(clock.catalog).forEach{city->
                val count=f.albums.first{it.cityId==city.id}.photos.size
                GlassAction({clock.selectFootprint(city)},Modifier.fillMaxWidth().height(64.dp),enabled=available){
                    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
                        Text(city.name,style=uiText(16),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(if(count==0)"还没有照片" else "$count 张照片",style=uiText(12),color=PicoTheme.colorScheme.labelSecondary)
                    }
                }
            }
        }
        if(!f.initialized){
            Text(if(f.busy)"正在载入…" else f.error,style=uiText(13))
            if(!f.busy)GlassAction({f.initialize(context)},Modifier.fillMaxWidth().height(40.dp)){Text("重试",style=uiText(14))}
        }
        GlassAction({clock.touchUi();f.searchPlaces()},Modifier.fillMaxWidth().height(40.dp).semantics{contentDescription="添加足迹地点"},enabled=available){
            Text("＋",style=uiText(20))
        }
    }
}

@Composable internal fun FootprintAlbumPanel(clock:ClockState,choosePhotos:(android.view.View)->Unit) {
    val sourceView=LocalView.current
    val f=clock.footprints;val city=f.city(clock.catalog)
    if(!f.enabled||!f.albumOpen||f.placesOpen||city==null)return
    val album=f.album?:return
    key(city.id){
        // Closing the viewer preserves the tray scroll instead of constructing it at item zero.
        val trayScroll=androidx.compose.foundation.lazy.rememberLazyListState()
        val viewerScroll=androidx.compose.foundation.lazy.rememberLazyListState()
        val expanded=f.viewerOpen
        LaunchedEffect(expanded){
            if(expanded)viewerScroll.scrollToItem(album.photos.indexOf(f.photo).coerceAtLeast(0))
        }
        Glass(Modifier.width(if(expanded)900.dp else 624.dp).alpha(f.albumAlpha)
            .clickable(interactionSource=remember{MutableInteractionSource()},indication=null){clock.touchUi()},
            padding=0.dp,spacing=0.dp){
            Column(Modifier.padding(horizontal=24.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(if(expanded)12.dp else 16.dp)){
                Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("${city.name} · ${city.country}",style=uiText(20,FontWeight.Medium),modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(!expanded)GlassAction({clock.touchUi();choosePhotos(sourceView)},Modifier.size(144.dp,48.dp),enabled=!f.busy&&f.pickingCityId==null){
                        Text("＋ 添加照片",style=uiText(16))
                    }
                    PanelClose(if(expanded)"返回缩略图" else "关闭相册"){
                        clock.touchUi()
                        if(expanded)f.closeViewer()else{f.closeAlbum();clock.resumeRequest++}
                    }
                }
                if(expanded){
                    // Fit always preserves portrait/landscape ratio; no forced crop in viewer.
                    LocalPhoto(f.images,f.photo,Modifier.fillMaxWidth().height(432.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha=.75f)),hero=true)
                    Row(Modifier.fillMaxWidth().height(24.dp),verticalAlignment=Alignment.CenterVertically){
                        Text(f.photo?.takenDate?:"拍摄日期暂无",style=uiText(14),modifier=Modifier.weight(1f),color=PicoTheme.colorScheme.labelSecondary)
                        Text("${album.photos.indexOf(f.photo)+1} / ${album.photos.size}",style=uiText(14))
                    }
                }
                if(album.photos.isEmpty()){
                    Column(Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha=.18f)),
                        verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                        Text("还没有照片",style=uiText(18,FontWeight.Medium))
                        Spacer(Modifier.height(8.dp))
                        Text("添加照片，留在这个地点。",style=uiText(14),color=PicoTheme.colorScheme.labelSecondary)
                    }
                }else{
                    LazyRow(Modifier.fillMaxWidth().height(108.dp),state=if(expanded)viewerScroll else trayScroll,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        items(album.photos,key={it.id}){photo->
                            val chosen=expanded&&photo.id==f.photo?.id
                            LocalPhoto(f.images,photo,Modifier.size(184.dp,108.dp).clip(RoundedCornerShape(12.dp))
                                .border(if(chosen)3.dp else 1.dp,Color.White.copy(alpha=if(chosen)1f else .25f),RoundedCornerShape(12.dp))
                                .semantics{contentDescription="放大第 ${album.photos.indexOf(photo)+1} 张照片";selected=chosen}
                                .spatialHoverEffect(SpatialHoverStyle.Highlight)
                                .clickable{clock.touchUi();f.openPhoto(photo.id)})
                        }
                    }
                }
                if(f.progress.isNotBlank())Text(f.progress,style=uiText(14))
                if(f.error.isNotBlank()&&f.errorCityId==city.id){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Text(f.error,style=uiText(14),modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                        GlassAction({clock.touchUi();choosePhotos(sourceView)},Modifier.height(40.dp),enabled=!f.busy&&f.pickingCityId==null){Text("重试",style=uiText(14))}
                    }
                }
            }
        }
    }
}
