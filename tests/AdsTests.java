package dz.electrogas.tests;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import dz.electrogas.*;
import java.lang.reflect.*;
import java.util.*;

/** Tests real Activity integration and privacy cancellation, without claiming a filled ad. */
public final class AdsTests extends Instrumentation {
    int checks;Activity activity;SharedPreferences prefs;Map<String,?> original;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    View find(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),tag);if(x!=null)return x;}}return null;}
    View view(String tag){return find(activity.getWindow().getDecorView(),tag);}
    void click(String tag){runOnMainSync(()->{View v=view(tag);if(v==null)throw new AssertionError("Missing "+tag);v.performClick();});waitForIdleSync();}
    Object controller()throws Exception{Field f=MainActivity.class.getDeclaredField("ads");f.setAccessible(true);return f.get(activity);}
    void enable(boolean value)throws Exception{Object ads=controller();Method m=ads.getClass().getDeclaredMethod("setEnabled",boolean.class);m.setAccessible(true);runOnMainSync(()->{try{m.invoke(ads,value);}catch(Exception e){throw new RuntimeException(e);}});}
    boolean hasBanner(){boolean[] yes={false};runOnMainSync(()->yes[0]=view("ads.banner")!=null);return yes[0];}
    void launch(){activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));waitForIdleSync();}
    @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_OK;try{
        prefs=getTargetContext().getSharedPreferences("ad_privacy",0);original=prefs.getAll();prefs.edit().clear().commit();
        launch();check(!prefs.getBoolean("google_test_ads",false),"ads default off");check(!hasBanner(),"no AdView before opt-in");
        click("calculator.tab.3");check(!hasBanner(),"tab change does not enable ads");
        click("ads.settings");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        check(!prefs.getBoolean("google_test_ads",false)&&!hasBanner(),"dismissed disclosure does not enable ads");
        enable(true);check(prefs.getBoolean("google_test_ads",false),"explicit activation stored");
        enable(false);check(!prefs.getBoolean("google_test_ads",true),"withdrawal stored");check(!hasBanner(),"withdrawal removes banner immediately");
        // Let actual UMP callbacks arrive; obsolete callbacks must not restore advertising.
        android.os.SystemClock.sleep(5000);waitForIdleSync();check(!hasBanner(),"late consent callback cannot request a banner after withdrawal");
        click("calculator.tab.0");check(!hasBanner(),"re-render preserves withdrawal");
        runOnMainSync(()->activity.finish());launch();check(!hasBanner()&&!prefs.getBoolean("google_test_ads",true),"withdrawal survives Activity recreation");
        enable(true);
        long deadline=android.os.SystemClock.uptimeMillis()+60000;
        while(!hasBanner()&&android.os.SystemClock.uptimeMillis()<deadline)android.os.SystemClock.sleep(250);
        check(hasBanner(),"real sample AdView created after explicit opt-in and UMP completion");
        click("calculator.tab.1");check(hasBanner(),"banner retained across tab reconstruction");
        enable(false);check(!hasBanner(),"initialized SDK banner removed on withdrawal");
        android.content.pm.ApplicationInfo app=getTargetContext().getPackageManager().getApplicationInfo("dz.electrogas",android.content.pm.PackageManager.GET_META_DATA);
        check("ca-app-pub-3940256099942544~3347511713".equals(app.metaData.getString("com.google.android.gms.ads.APPLICATION_ID")),"packaged official test app ID");
        check(app.metaData.getBoolean("com.google.android.gms.ads.DELAY_APP_MEASUREMENT_INIT"),"measurement initialization delayed");
        List<String> permissions=Arrays.asList(getTargetContext().getPackageManager().getPackageInfo("dz.electrogas",android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions);
        check(permissions.contains("android.permission.INTERNET"),"network permission packaged");
        check(!permissions.contains("com.google.android.gms.permission.AD_ID")&&!permissions.contains("android.permission.ACCESS_ADSERVICES_TOPICS"),"advertising ID and topics permissions removed");
        result.putString("result","PASS: "+checks+" ad privacy / lifecycle checks. No assertion of a filled Google ad.");
    }catch(Throwable e){code=Activity.RESULT_CANCELED;result.putString("result","FAIL: "+e);android.util.Log.e("AdsTests","Failed",e);}finally{
        if(activity!=null)runOnMainSync(()->activity.finish());
        if(original!=null){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:original.entrySet())if(e.getValue() instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)e.getValue());edit.commit();}
    }finish(code,result);}
}
