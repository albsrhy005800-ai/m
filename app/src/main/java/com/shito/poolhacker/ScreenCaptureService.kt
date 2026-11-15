package com.shito.poolhacker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureService:Service(){
 companion object { const val EXTRA_RESULT_CODE="result_code"; const val EXTRA_RESULT_DATA="result_data"; private const val CHANNEL="shito_capture" }
 private var projection:MediaProjection?=null; private var reader:ImageReader?=null; private var display:VirtualDisplay?=null
 private var thread:HandlerThread?=null; private var handler:Handler?=null; private var lastFrameAt=0L
 override fun onCreate(){super.onCreate()
  if(Build.VERSION.SDK_INT>=26){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"تحليل شاشة اللعبة",NotificationManager.IMPORTANCE_LOW))}
  val note=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,CHANNEL) else Notification.Builder(this)
  val n=note.setContentTitle("Shito Pool").setContentText("تحليل الطاولة والكرات").setSmallIcon(android.R.drawable.ic_menu_view).build()
  if(Build.VERSION.SDK_INT>=29) startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(7,n)
  thread=HandlerThread("shito-analyzer").also{it.start()};handler=Handler(thread!!.looper)
 }
 override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{
  val code=i?.getIntExtra(EXTRA_RESULT_CODE,0)?:0
  @Suppress("DEPRECATION") val data=i?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
  if(data==null||code==0){stopSelf();return START_NOT_STICKY}
  val mgr=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  projection=mgr.getMediaProjection(code,data)
  val dm=DisplayMetrics();@Suppress("DEPRECATION") (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
  reader=ImageReader.newInstance(dm.widthPixels,dm.heightPixels,PixelFormat.RGBA_8888,2)
  reader!!.setOnImageAvailableListener({ir-> val image=ir.acquireLatestImage()?:return@setOnImageAvailableListener
   val now=System.currentTimeMillis()
   if(now-lastFrameAt<350){image.close();return@setOnImageAvailableListener}
   lastFrameAt=now
   try{val p=image.planes[0];val pad=p.rowStride/p.pixelStride-dm.widthPixels
    val full=Bitmap.createBitmap(dm.widthPixels+pad,dm.heightPixels,Bitmap.Config.ARGB_8888);full.copyPixelsFromBuffer(p.buffer)
    val bmp=Bitmap.createBitmap(full,0,0,dm.widthPixels,dm.heightPixels);full.recycle()
    AimOverlayService.updateDetection(ScreenAnalyzer.analyze(bmp));bmp.recycle()
   }catch(_:Exception){}finally{image.close()}
  },handler)
  projection!!.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},handler)
  display=projection!!.createVirtualDisplay("ShitoCapture",dm.widthPixels,dm.heightPixels,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
  return START_NOT_STICKY
 }
 override fun onDestroy(){display?.release();reader?.close();projection?.stop();thread?.quitSafely();super.onDestroy()}
 override fun onBind(i:Intent?)=null
}