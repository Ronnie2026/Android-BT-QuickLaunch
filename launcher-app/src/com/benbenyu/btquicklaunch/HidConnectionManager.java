package com.benbenyu.btquicklaunch;
import android.bluetooth.*;import android.content.*;import android.os.*;
/** Android 11 HID-host IPC, verified against this device's platform Stub. */
final class HidConnectionManager {
 static volatile int currentState = -1;
 static volatile int batteryLevel = -1;
 static volatile Runnable onStateChanged;
 static final java.util.UUID UUID_BATTERY_SERVICE = java.util.UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
 static final java.util.UUID UUID_BATTERY_LEVEL = java.util.UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
 boolean batteryQueried;
 final Context context;final HandlerThread worker=new HandlerThread("HidReconnect");Handler h;volatile IBinder binder;volatile boolean started,binding;long nextAttempt,nextBind;int retry,lastState=-1;final long[] delays={2000,5000,10000};
 HidConnectionManager(Context c){context=c;}
 final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder service){binder=service;RootHidProvider.hid=service;AppLog.write("HID_PROFILE_READY direct_ipc",null);signal();}public void onServiceDisconnected(ComponentName name){binder=null;RootHidProvider.hid=null;AppLog.write("HID_PROFILE_LOST",null);}public void onBindingDied(ComponentName name){binder=null;binding=false;try{context.unbindService(this);}catch(Exception ignored){}signal();}};
 void bind(){if(binding||Build.VERSION.SDK_INT!=30)return;binding=context.bindService(new Intent("android.bluetooth.IBluetoothHidHost").setClassName("com.android.bluetooth","com.android.bluetooth.hid.HidHostService"),connection,Context.BIND_AUTO_CREATE);AppLog.write("HID_BIND_REQUEST "+binding,null);}
 void start(){if(started)return;started=true;worker.start();h=new Handler(worker.getLooper());try{bind();}catch(Exception e){binding=false;AppLog.write("HID_BIND_ERROR",e);}h.post(tick);}
 void signal(){if(h==null||!started)return;h.post(()->{retry=0;nextAttempt=0;h.removeCallbacks(tick);h.post(tick);});}
 void onAclConnected(){if(h==null||!started)return;h.post(()->{retry=0;nextAttempt=SystemClock.elapsedRealtime()+2500;h.removeCallbacks(tick);h.postDelayed(tick,2500);});}
 void stop(){started=false;if(h!=null)h.removeCallbacksAndMessages(null);if(binding)try{context.unbindService(connection);}catch(Exception ignored){}binding=false;binder=null;worker.quitSafely();}
 int call(int transaction,BluetoothDevice device)throws Exception{IBinder service=binder;if(service==null||!"android.bluetooth.IBluetoothHidHost".equals(service.getInterfaceDescriptor()))throw new IllegalStateException("HID interface unavailable");Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{data.writeInterfaceToken("android.bluetooth.IBluetoothHidHost");data.writeInt(1);device.writeToParcel(data,0);if(!service.transact(transaction,data,reply,0))throw new IllegalStateException("Unsupported HID transaction");reply.readException();return reply.readInt();}finally{data.recycle();reply.recycle();}}
 final Runnable tick=new Runnable(){public void run(){if(!started)return;long now=SystemClock.elapsedRealtime();try{BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(binder==null){if(now>=nextBind&&!binding){nextBind=now+30000;bind();}}else if(adapter!=null&&adapter.isEnabled()){BluetoothDevice device=adapter.getRemoteDevice(DeviceConfig.KEYBOARD_MAC);int state=call(5,device);currentState=state;if(state!=lastState){int prev=lastState;AppLog.write("HID_STATE "+prev+" -> "+state,null);lastState=state;if(state==BluetoothProfile.STATE_CONNECTED){batteryQueried=false;queryBattery(device);if(prev==BluetoothProfile.STATE_DISCONNECTED||prev==BluetoothProfile.STATE_CONNECTING)playConnectedTone();}else if(state==BluetoothProfile.STATE_DISCONNECTED){batteryQueried=false;batteryLevel=-1;}Runnable cb=onStateChanged;if(cb!=null)cb.run();}if(state==BluetoothProfile.STATE_CONNECTED){retry=0;nextAttempt=0;if(!batteryQueried&&batteryLevel<0)queryBattery(device);}else if(state==BluetoothProfile.STATE_DISCONNECTED&&device.getBondState()==BluetoothDevice.BOND_BONDED&&retry<delays.length&&now>=nextAttempt){String apk=context.getApplicationInfo().sourceDir;if(apk.indexOf('\'')>=0)throw new IllegalStateException("Unexpected APK path");java.lang.Process child=new ProcessBuilder("su","-c","CLASSPATH='"+apk+"' app_process /system/bin com.benbenyu.btquicklaunch.RootHidConnect").redirectErrorStream(true).start();if(!child.waitFor(8,java.util.concurrent.TimeUnit.SECONDS)){child.destroy();throw new IllegalStateException("Root HID timeout");}int result=child.exitValue()==0?1:0;AppLog.write("HID_RETRY result="+result+" retry="+retry,null);nextAttempt=now+delays[retry];retry++;if(retry>=delays.length)AppLog.write("HID_SLEEP_WAIT",null);}}}catch(Exception e){AppLog.write("HID_RETRY_ERROR",e);nextAttempt=now+30000;}if(started)h.postDelayed(this,2000);}};
 void playConnectedTone(){
  if(!context.getSharedPreferences("home",0).getBoolean("bt_connect_sound",true))return;
  try{
   android.media.ToneGenerator tg=new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,80);
   tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK,180);
   AppLog.write("BT_CONNECT_TONE played",null);
   if(h!=null)h.postDelayed(tg::release,450);
  }catch(Exception e){AppLog.write("BT_CONNECT_TONE_ERROR "+e.getClass().getSimpleName(),null);}
 }
 void queryBattery(BluetoothDevice device){
  batteryQueried=true;
  try{
   int sys=(Integer)BluetoothDevice.class.getMethod("getBatteryLevel").invoke(device);
   if(sys>=0&&sys<=100){
    batteryLevel=sys;
    AppLog.write("BT_BATTERY source=system level="+sys,null);
    Runnable cb=onStateChanged;if(cb!=null)cb.run();
    return;
   }
  }catch(Exception ignored){}
  try{
   device.connectGatt(context,false,new BluetoothGattCallback(){
    public void onConnectionStateChange(BluetoothGatt gatt,int status,int newState){
     if(status==BluetoothGatt.GATT_SUCCESS&&newState==BluetoothProfile.STATE_CONNECTED){if(!gatt.discoverServices())gatt.close();}
     else if(newState==BluetoothProfile.STATE_DISCONNECTED||status!=BluetoothGatt.GATT_SUCCESS){gatt.close();}
    }
    public void onServicesDiscovered(BluetoothGatt gatt,int status){
     if(status==BluetoothGatt.GATT_SUCCESS){
      BluetoothGattService svc=gatt.getService(UUID_BATTERY_SERVICE);
      BluetoothGattCharacteristic ch=svc!=null?svc.getCharacteristic(UUID_BATTERY_LEVEL):null;
      if(ch!=null&&gatt.readCharacteristic(ch))return;
     }
     AppLog.write("BT_BATTERY source=gatt unavailable",null);
     gatt.close();
    }
    public void onCharacteristicRead(BluetoothGatt gatt,BluetoothGattCharacteristic ch,int status){
     if(status==BluetoothGatt.GATT_SUCCESS&&UUID_BATTERY_LEVEL.equals(ch.getUuid())){
      Integer v=ch.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8,0);
      if(v!=null&&v>=0&&v<=100){
       batteryLevel=v;
       AppLog.write("BT_BATTERY source=gatt level="+v,null);
       Runnable cb=onStateChanged;if(cb!=null)cb.run();
      }
     }
     gatt.close();
    }
   },BluetoothDevice.TRANSPORT_LE);
  }catch(Exception e){AppLog.write("BT_BATTERY_ERROR",e);}
 }
}
